package com.etendoerp.setup

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

import java.awt.Desktop
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Gradle task that authenticates with GitHub using the Device Flow.
 *
 * Usage:
 *   ./gradlew setup.githubAuth
 *
 * The task opens GitHub's device activation page, waits for the user
 * to authorize, then persists the resulting token in gradle.properties.
 */
class GithubAuthTask extends DefaultTask {

    private static final String CLIENT_ID = 'Ov23li1PuCseVXZZVH6O'
    private static final String SCOPE = 'read:packages'
    private static final String DEVICE_CODE_URL = 'https://github.com/login/device/code'
    private static final String ACCESS_TOKEN_URL = 'https://github.com/login/oauth/access_token'

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

    GithubAuthTask() {
        group = 'setup'
        description = 'Authenticate with GitHub via Device Flow and save token to gradle.properties'
    }

    @TaskAction
    void execute() {
        // Step 1: Start device flow
        def deviceResponse = startDeviceFlow()

        String deviceCode = deviceResponse.device_code
        String userCode = deviceResponse.user_code
        String verificationUri = deviceResponse.verification_uri
        int expiresIn = deviceResponse.expires_in as int
        int interval = deviceResponse.interval as int

        // Try to open the browser automatically
        boolean browserOpened = tryOpenBrowser(verificationUri)

        project.logger.lifecycle('')
        project.logger.lifecycle('╔══════════════════════════════════════════════════╗')
        project.logger.lifecycle('║         GitHub Authentication Required           ║')
        project.logger.lifecycle('╠══════════════════════════════════════════════════╣')
        if (browserOpened) {
            project.logger.lifecycle('║  A browser window was opened for you.           ║')
        } else {
            project.logger.lifecycle('║  Open the following URL in your browser:        ║')
            project.logger.lifecycle("║  ${verificationUri.padRight(49)}║")
        }
        project.logger.lifecycle('║                                                  ║')
        project.logger.lifecycle('║  Enter this code when prompted:                 ║')
        project.logger.lifecycle("║  >>> ${userCode.padRight(44)}║")
        project.logger.lifecycle('╚══════════════════════════════════════════════════╝')
        project.logger.lifecycle('')
        project.logger.lifecycle('Waiting for authorization (do not close this terminal)...')

        // Step 2: Poll for access token
        String accessToken = pollForToken(deviceCode, expiresIn, interval)

        // Step 3: Save token to gradle.properties
        saveGithubToken(accessToken)

        project.logger.lifecycle('')
        project.logger.lifecycle('✓ Token saved to gradle.properties')
        String masked = accessToken.length() > 8 ?
                accessToken.substring(0, 4) + '...' + accessToken.substring(accessToken.length() - 4) :
                '****'
        project.logger.lifecycle("  githubToken=${masked}")
    }

    private static boolean tryOpenBrowser(String url) {
        try {
            String os = System.getProperty('os.name').toLowerCase()
            if (os.contains('mac')) {
                Runtime.getRuntime().exec(['open', url] as String[])
                return true
            } else if (os.contains('win')) {
                Runtime.getRuntime().exec(['rundll32', 'url.dll,FileProtocolHandler', url] as String[])
                return true
            } else if (os.contains('nux') || os.contains('nix')) {
                Runtime.getRuntime().exec(['xdg-open', url] as String[])
                return true
            }
        } catch (Exception ignored) {
            // Fall through — user will open manually
        }
        // Fallback: try java.awt.Desktop
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url))
                return true
            }
        } catch (Exception ignored) {}
        return false
    }

    private static Map startDeviceFlow() {
        String body = "client_id=${CLIENT_ID}&scope=${SCOPE}"

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DEVICE_CODE_URL))
                .header('Accept', 'application/json')
                .header('Content-Type', 'application/x-www-form-urlencoded')
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
        def json = new groovy.json.JsonSlurper().parseText(response.body())

        if (json.error) {
            throw new GradleException("GitHub device code request failed: ${json.error_description ?: json.error}")
        }

        return json as Map
    }

    private static String pollForToken(String deviceCode, int expiresIn, int interval) {
        String body = "client_id=${CLIENT_ID}&device_code=${deviceCode}&grant_type=urn:ietf:params:oauth:grant-type:device_code"

        long deadline = System.currentTimeMillis() + (expiresIn * 1000L)
        int currentInterval = interval

        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(currentInterval * 1000L)

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ACCESS_TOKEN_URL))
                    .header('Accept', 'application/json')
                    .header('Content-Type', 'application/x-www-form-urlencoded')
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build()

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
            def json = new groovy.json.JsonSlurper().parseText(response.body())

            if (json.access_token) {
                return json.access_token
            }

            switch (json.error) {
                case 'authorization_pending':
                    // User hasn't authorized yet, keep polling
                    break
                case 'slow_down':
                    currentInterval += 5
                    break
                case 'expired_token':
                    throw new GradleException('GitHub authorization expired. Please run the task again.')
                case 'access_denied':
                    throw new GradleException('GitHub authorization was denied by the user.')
                default:
                    throw new GradleException("GitHub auth error: ${json.error_description ?: json.error}")
            }
        }

        throw new GradleException('GitHub authorization timed out. Please run the task again.')
    }

    private void saveGithubToken(String token) {
        File propsFile = project.file('gradle.properties')

        if (!propsFile.exists()) {
            propsFile.text = "githubToken=${token}\n"
            return
        }

        String content = propsFile.text
        if (content =~ /(?m)^githubToken=.*$/) {
            content = content.replaceAll(/(?m)^githubToken=.*$/, "githubToken=${token}")
        } else {
            if (!content.endsWith('\n')) {
                content += '\n'
            }
            content += "githubToken=${token}\n"
        }
        propsFile.text = content
    }
}
