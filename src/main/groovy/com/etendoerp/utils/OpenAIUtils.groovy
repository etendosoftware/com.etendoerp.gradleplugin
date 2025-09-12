package com.etendoerp.utils

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.gradle.api.Project

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

class OpenAIUtils {

    /**
     * Calls OpenAI API with the provided parameters
     * 
     * @param project Gradle project for logging and properties access
     * @param systemPrompt The system message to set context for the AI
     * @param messages Array of message objects with 'role' and 'content' properties
     * @param model The OpenAI model to use (default: 'gpt-4o-mini')
     * @param apiKey Optional API key, if not provided will read from project properties
     * @param temperature Optional temperature setting (default: 0.1)
     * @param maxTokens Optional max tokens setting (default: 1000)
     * @param timeout Optional timeout in seconds (default: 60)
     * @return The AI response text or null if error
     */
    static String callOpenAI(Project project, 
                           String systemPrompt, 
                           List<Map<String, String>> messages, 
                           String model = 'gpt-5-nano',
                           String apiKey = null,
                           Double temperature = 1,
                           Integer timeout = 60) {
        try {
            // Get API key from parameter or project properties
            String openaiApiKey = apiKey ?: project.findProperty('OPENAI_API_KEY')?.toString()
            
            if (!openaiApiKey) {
                project.logger.error("OPENAI_API_KEY is required but not found in gradle.properties or provided as parameter")
                return null
            }

            if (openaiApiKey.trim().isEmpty()) {
                project.logger.error("OPENAI_API_KEY is empty")
                return null
            }

            // Build the messages array starting with system prompt
            def apiMessages = []
            if (systemPrompt) {
                apiMessages.add([
                    role: "system",
                    content: systemPrompt
                ])
            }
            
            // Add user messages
            if (messages) {
                apiMessages.addAll(messages)
            }

            def requestBody = new JsonBuilder([
                model: model,
                messages: apiMessages,
                temperature: temperature
            ]).toString()

            def client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build()

            def request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${openaiApiKey}")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(timeout))
                .build()

            def response = client.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() != 200) {
                project.logger.error("OpenAI API error: ${response.statusCode()} - ${response.body()}")
                return null
            }

            def jsonResponse = new JsonSlurper().parseText(response.body())
            def responseText = jsonResponse.choices[0].message.content.toString().trim()
            
            project.logger.debug("OpenAI API call successful")
            
            return responseText
            
        } catch (Exception e) {
            project.logger.error("Error calling OpenAI API: ${e.message}", e)
            return null
        }
    }
}
