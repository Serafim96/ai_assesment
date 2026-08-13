package ru.assistant.llm;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HttpLlmClientTest {

    private MockWebServer server;
    private OkHttpClient httpClient;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        httpClient = new OkHttpClient();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private HttpLlmClient newClient() {
        String endpoint = server.url("/v1/chat/completions").toString();
        return new HttpLlmClient(httpClient, endpoint, "test-model", "test-api-key", 5000L);
    }

    private List<ChatMessage> sampleMessages() {
        return Collections.singletonList(ChatMessage.user("What's the weather in Moscow?"));
    }

    @Test
    public void parsesSuccessfulResponseWithToolCalls() throws Exception {
        String body = "{"
                + "\"choices\":[{"
                + "\"message\":{"
                + "\"content\":null,"
                + "\"tool_calls\":[{"
                + "\"id\":\"call_1\","
                + "\"type\":\"function\","
                + "\"function\":{\"name\":\"get_weather\",\"arguments\":\"{\\\"city\\\":\\\"Moscow\\\"}\"}"
                + "}]"
                + "},"
                + "\"finish_reason\":\"tool_calls\""
                + "}]"
                + "}";
        server.enqueue(new MockResponse().setResponseCode(200).setBody(body));

        HttpLlmClient client = newClient();
        ChatResponse response = client.chat(sampleMessages(), Collections.<ToolSpec>emptyList());

        assertNull(response.getContent());
        assertEquals("tool_calls", response.getFinishReason());
        assertEquals(1, response.getToolCalls().size());
        ToolCall call = response.getToolCalls().get(0);
        assertEquals("call_1", call.getId());
        assertEquals("get_weather", call.getName());
        assertEquals("{\"city\":\"Moscow\"}", call.getArgumentsJson());

        assertEquals(1, server.getRequestCount());
        RecordedRequest recorded = server.takeRequest();
        assertEquals("Bearer test-api-key", recorded.getHeader("Authorization"));
        assertTrue(recorded.getBody().readUtf8().contains("\"model\":\"test-model\""));
    }

    @Test
    public void retriesOnceAfterServerErrorThenSucceeds() throws Exception {
        String successBody = "{\"choices\":[{\"message\":{\"content\":\"hello there\"},\"finish_reason\":\"stop\"}]}";
        server.enqueue(new MockResponse().setResponseCode(500).setBody("internal error"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody(successBody));

        HttpLlmClient client = newClient();
        ChatResponse response = client.chat(sampleMessages(), Collections.<ToolSpec>emptyList());

        assertEquals("hello there", response.getContent());
        assertEquals("stop", response.getFinishReason());
        assertTrue(response.getToolCalls().isEmpty());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void failsAfterSingleRetryOnRepeatedServerErrors() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("internal error 1"));
        server.enqueue(new MockResponse().setResponseCode(500).setBody("internal error 2"));

        HttpLlmClient client = newClient();
        try {
            client.chat(sampleMessages(), Collections.<ToolSpec>emptyList());
            fail("Expected LlmException after repeated server errors");
        } catch (LlmException e) {
            assertTrue(e.getMessage().contains("500"));
        }

        assertEquals(2, server.getRequestCount());
    }
}
