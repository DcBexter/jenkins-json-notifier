package org.jenkinsci.plugins.notify;

import static org.junit.Assert.*;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.scriptsecurity.scripts.languages.GroovyLanguage;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.JenkinsRule;

public class NotifyRecorderTest {
    @Rule public JenkinsRule j = new JenkinsRule();
    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final AtomicReference<String> method = new AtomicReference<>();
    private int status = 204;

    @Before public void startEndpoint() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/notify", exchange -> {
            requests.incrementAndGet();
            method.set(exchange.getRequestMethod());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
    }

    @After public void stopEndpoint() {
        if (server != null) server.stop(0);
        System.clearProperty("notify-plugin-test-executed");
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/notify";
    }

    private FreeStyleProject project(String template) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getPublishersList().add(new NotifyRecorder(url(), template));
        return project;
    }

    @Test public void defaultTemplatePostsValidJsonWithoutScmOrUserCause() throws Exception {
        FreeStyleProject project = project(null);
        FreeStyleBuild build = j.buildAndAssertSuccess(project);
        assertEquals(1, requests.get());
        assertEquals("POST", method.get());
        assertTrue(contentType.get().startsWith("application/json"));
        var json = JsonParser.parseString(body.get()).getAsJsonObject();
        assertEquals(project.getFullName(), json.get("job").getAsString());
        assertEquals(build.getNumber(), json.get("number").getAsInt());
        assertEquals("SUCCESS", json.get("result").getAsString());
    }

    @Test public void customTemplateCannotExecuteUntilApproved() throws Exception {
        String template = "<% System.setProperty('notify-plugin-test-executed', 'yes') %>"
                + "{\"message\": ${json('quote \\\" and newline\\n')}}";
        FreeStyleProject project = project(template);
        FreeStyleBuild blocked = j.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0));
        j.assertLogContains("In-process Script Approval", blocked);
        assertNull(System.getProperty("notify-plugin-test-executed"));
        assertEquals(0, requests.get());
        assertEquals(1, ScriptApproval.get().getPendingScripts().size());

        ScriptApproval.get().preapprove(NotifyRecorder.templateSource(template), GroovyLanguage.get());
        j.buildAndAssertSuccess(project);
        assertEquals("yes", System.getProperty("notify-plugin-test-executed"));
        assertEquals("quote \" and newline\n", JsonParser.parseString(body.get()).getAsJsonObject().get("message").getAsString());
    }

    @Test public void configurationRoundTripKeepsExistingFieldNames() throws Exception {
        FreeStyleProject project = project("{\"custom\":true}");
        j.configRoundtrip(project);
        NotifyRecorder recorder = project.getPublishersList().get(NotifyRecorder.class);
        assertEquals(url(), recorder.notifyUrl);
        assertEquals("{\"custom\":true}", recorder.notifyTemplate);
    }

    @Test public void failedBuildAndBlankEndpointDoNotNotify() throws Exception {
        FreeStyleProject failed = project(null);
        failed.getBuildersList().add(new FailureBuilder());
        j.assertBuildStatus(Result.FAILURE, failed.scheduleBuild2(0));
        FreeStyleProject disabled = j.createFreeStyleProject();
        disabled.getPublishersList().add(new NotifyRecorder(" ", null));
        j.buildAndAssertSuccess(disabled);
        assertEquals(0, requests.get());
    }

    @Test public void invalidJsonFailsWithoutLoggingPayload() throws Exception {
        String template = "{secret-invalid-json}";
        FreeStyleProject project = project(template);
        ScriptApproval.get().preapprove(NotifyRecorder.templateSource(template), GroovyLanguage.get());
        FreeStyleBuild build = j.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0));
        j.assertLogContains("Failed to render or validate notification JSON", build);
        j.assertLogNotContains("secret-invalid-json", build);
        assertEquals(0, requests.get());
    }

    @Test public void httpFailureDoesNotLeakPayloadOrWebhookToken() throws Exception {
        status = 500;
        String template = "{\"secret\":\"private-payload\"}";
        FreeStyleProject project = j.createFreeStyleProject();
        project.getPublishersList().add(new NotifyRecorder(url() + "?token=private-token", template));
        ScriptApproval.get().preapprove(NotifyRecorder.templateSource(template), GroovyLanguage.get());
        FreeStyleBuild build = j.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0));
        j.assertLogContains("HTTP 500; expected 204", build);
        j.assertLogNotContains("private-payload", build);
        j.assertLogNotContains("private-token", build);
        assertEquals(1, requests.get());
    }

    @Test public void malformedWebhookDoesNotLeakItsToken() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getPublishersList().add(new NotifyRecorder(url() + "?token=private token", null));
        FreeStyleBuild build = j.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0));
        j.assertLogContains("Notification request failed", build);
        j.assertLogNotContains("private token", build);
        assertEquals(0, requests.get());
    }
}
