package com.github.gradusnikov.eclipse.assistai.services;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import org.eclipse.core.runtime.ILog;
import org.eclipse.e4.core.di.annotations.Creatable;
import org.eclipse.swt.graphics.ImageData;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.gradusnikov.eclipse.assistai.commands.FunctionExecutorProvider;
import com.github.gradusnikov.eclipse.assistai.model.ChatMessage;
import com.github.gradusnikov.eclipse.assistai.model.Conversation;
import com.github.gradusnikov.eclipse.assistai.model.Incoming;
import com.github.gradusnikov.eclipse.assistai.part.Attachment;
import com.github.gradusnikov.eclipse.assistai.prompt.PromptLoader;
import com.github.gradusnikov.eclipse.assistai.tools.ImageUtilities;

import jakarta.inject.Inject;

/**
 * A Java HTTP client for streaming requests to OpenAI API.
 * This class allows subscribing to responses received from the OpenAI API and processes the chat completions.
 */
@Creatable
public class OpenAIStreamJavaHttpClient
{
    private SubmissionPublisher<Incoming> publisher;
    
    private Supplier<Boolean> isCancelled = () -> false;
    
    
    
    @Inject
    private ILog logger;
    
    @Inject
    private PromptLoader promptLoader;
    
    @Inject
    private OpenAIClientConfiguration configuration;
    
    @Inject
    private FunctionExecutorProvider functionExecutor;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    
    public OpenAIStreamJavaHttpClient()
    {
        publisher = new SubmissionPublisher<>();

    }
    
    public void setCancelProvider( Supplier<Boolean> isCancelled )
    {
        this.isCancelled = isCancelled;
    }
    
    /**
     * Subscribes a given Flow.Subscriber to receive String data from OpenAI API responses.
     * @param subscriber the Flow.Subscriber to be subscribed to the publisher
     */
    public synchronized void subscribe(Flow.Subscriber<Incoming> subscriber)
    {
        publisher.subscribe(subscriber);
    }
    
    /**
     * Returns the JSON request body as a String for the given prompt.
     * @param prompt the user input to be included in the request body
     * @return the JSON request body as a String
     */
    private String getRequestBody_dice(Conversation prompt)
    {
        try
        {
            var objectMapper = new ObjectMapper();
            var requestBody = new LinkedHashMap<String, Object>();
            var messages = new ArrayList<Map<String, Object>>();
    
            var systemMessage = new LinkedHashMap<String, Object> ();
            systemMessage.put("best_of", 1);
            systemMessage.put( "decoder_input_details", false);
            systemMessage.put("details", false);
            systemMessage.put("do_sample", true);
            systemMessage.put("max_new_tokens", 2048);
            systemMessage.put("repetition_penalty", 1.2);
            systemMessage.put("return_full_text", false);
            systemMessage.put("seed", 42);
            var stopText=new ArrayList<Object>();
            stopText.add("<|endoftext|>");
            systemMessage.put( "stop", stopText);
            systemMessage.put("temperature", 0.2);
            systemMessage.put("top_k", 50);
            systemMessage.put( "top_p", 0.95);
            systemMessage.put( "truncate", 1023);
            systemMessage.put( "typical_p", 0.95);
            systemMessage.put("watermark", false);
            
            
            
           // systemMessage.put("content", promptLoader.createPromptText("system-prompt.txt") );
            messages.add(systemMessage);
            

            
            requestBody.put("parameters", systemMessage);
           
            
            // TBD
            
            for ( ChatMessage message : prompt.messages() )
            {
              //  var userMessage = new LinkedHashMap<String,Object>();
             //   userMessage.put("role", message.getRole());

                List<ImageData> images = message.getAttachments()
                        .stream()
                        .map( Attachment::getImageData )
                        .filter( Objects::nonNull )
                        .collect( Collectors.toList() );

                List<String> textParts = message.getAttachments()
                        .stream()
                        .map( Attachment::toChatMessageContent )
                        .filter( Objects::nonNull )
                        .collect( Collectors.toList() );

                String textContent = "[INST]"+String.join( "\n", textParts ) + "\n\n" + message.getContent()+"[/INST]";

                if (images.isEmpty())
                {
                    if (Objects.nonNull( textContent ))
                    {
                    	requestBody.put("inputs", textContent);
                    	break;
                   //     userMessage.put("content", textContent);
                    }
                    if ( Objects.nonNull( message.getName() ) )
                    {
                      //  userMessage.put( "name", message.getName() );
                    }
                    if ( Objects.nonNull( message.getFunctionCall() ) )
                    {
                        var functionCall = new LinkedHashMap<String, String> ();
                        functionCall.put( "name", message.getFunctionCall().name() );
                        functionCall.put( "arguments", objectMapper.writeValueAsString(  message.getFunctionCall().arguments() ) );
                        
                      //  userMessage.put( "function_call", functionCall );
                    }
                }
               // messages.add(userMessage);
            }
            //requestBody.put("inputs", messages);
            
            //TBD
            
            
            String jsonString;
            jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody);
            return jsonString;
        }
        catch (JsonProcessingException e)
        {
            throw new RuntimeException( e );
        }
    }
    /**
     * Returns the JSON request body as a String for the given prompt.
     * @param prompt the user input to be included in the request body
     * @return the JSON request body as a String
     */
    private String getRequestBody(Conversation prompt)
    {
        try
        {
            var requestBody = new LinkedHashMap<String, Object>();
            var messages = new ArrayList<Map<String, Object>>();
    
            var systemMessage = new LinkedHashMap<String, Object> ();
            systemMessage.put("role", "system");
            systemMessage.put("content", promptLoader.createPromptText("system-prompt.txt") );
            messages.add(systemMessage);
            
            
            prompt.messages().stream().map( this::toJsonPayload ).forEach( messages::add );
            
            requestBody.put("model", isVisionEnabled() ? configuration.getVisionModelName() : configuration.getChatModelName() );
            requestBody.put("functions", AnnotationToJsonConverter.convertDeclaredFunctionsToJson( functionExecutor.get().getFunctions() ) );
            requestBody.put("messages", messages);
            requestBody.put("temperature", configuration.getModelTemperature());
            requestBody.put("stream", true);
    
            String jsonString;
            jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody);
            return jsonString;
        }
        catch (JsonProcessingException e)
        {
            throw new RuntimeException( e );
        }
    }

    private LinkedHashMap<String, Object> toJsonPayload( ChatMessage message )
    {
        try
        {
            var userMessage = new LinkedHashMap<String,Object>();
            userMessage.put("role", message.getRole());
            // function call results
            if ( Objects.nonNull( message.getName() ) )
            {
                userMessage.put( "name", message.getName() );
            }
            if ( Objects.nonNull( message.getFunctionCall() ) )
            {
                var functionCallObject = new LinkedHashMap<String, String> ();
                functionCallObject.put( "name", message.getFunctionCall().name() );
                functionCallObject.put( "arguments", objectMapper.writeValueAsString(  message.getFunctionCall().arguments() ) );
                userMessage.put( "function_call", functionCallObject );
            }
            
            var content = new ArrayList<>();
            
            // add text content
            List<String> textParts = message.getAttachments()
                    .stream()
                    .map( Attachment::toChatMessageContent )
                    .filter( Objects::nonNull )
                    .collect( Collectors.toList() );
            String textContent = String.join( "\n", textParts ) + "\n\n" + message.getContent();
            var textObject = new LinkedHashMap<String, String> ();
            textObject.put( "type", "text" );
            textObject.put( "text", textContent );
            content.add( textObject );
            
            // add image content
            if ( isVisionEnabled() )
            {
                message.getAttachments()
                       .stream()
                       .map( Attachment::getImageData )
                       .filter( Objects::nonNull )
                       .map( ImageUtilities::toBase64Jpeg )
                       .map( this::toImageUrl )
                       .forEachOrdered( content::add );
            }
            userMessage.put( "content", content );
            return userMessage;
        }
        catch ( JsonProcessingException e )
        {
            throw new RuntimeException( e );
        }
    }

    private boolean isVisionEnabled()
    {
        return !configuration.getVisionModelName().isBlank();            
    }
    
    private LinkedHashMap<String, String> toImageUrl( String data )
    {
        var imageObject = new LinkedHashMap<String, String> ();
        imageObject.put("type", "image_url");
        imageObject.put("image_url", "data:image/jpeg;base64," + data );
        return imageObject;
    }
    
    /**
     * Creates and returns a Runnable that will execute the HTTP request to OpenAI API
     * with the given conversation prompt and process the responses.
     * <p>
     * Note: this method does not block and the returned Runnable should be executed
     * to perform the actual HTTP request and processing.
     *
     * @param prompt the conversation to be sent to the OpenAI API
     * @return a Runnable that performs the HTTP request and processes the responses
     */
    public Runnable run( Conversation prompt ) 
    {
    	return () ->  {
    		var trustManager = new X509ExtendedTrustManager() {
    		    @Override
    		    public X509Certificate[] getAcceptedIssuers() {
    		        return new X509Certificate[]{};
    		    }

    		    @Override
    		    public void checkClientTrusted(X509Certificate[] chain, String authType) {
    		    }

    		    @Override
    		    public void checkServerTrusted(X509Certificate[] chain, String authType) {
    		    }

    		    @Override
    		    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
    		    }

    		    @Override
    		    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
    		    }

    		    @Override
    		    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
    		    }

    		    @Override
    		    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
    		    }
    		};
    		SSLContext sslContext = null;
			try {
				sslContext = SSLContext.getInstance("TLS");
				sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());
			} catch (NoSuchAlgorithmException | KeyManagementException e) {
				e.printStackTrace();
			}
    		
    		HttpClient client = HttpClient.newBuilder()
    		                              .connectTimeout( Duration.ofSeconds(configuration.getConnectionTimoutSeconds()) )
    		                              .sslContext(sslContext)
    		                              .build();
    		String requestBody = getRequestBody_dice(prompt);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(configuration.getApiUrl()))
                    .timeout( Duration.ofSeconds(60) )
                    .version(HttpClient.Version.HTTP_1_1)
    				//.header("Authorization", "Bearer " + configuration.getApiKey())
    				//.header("Accept", "text/event-stream")
    				.header("Accept",  "application/json")
    				.header("Content-Type", "application/json")
    				.header("api_key",  configuration.getApiKey())
    				.header("model_name",  configuration.getChatModelName())
    				.POST(HttpRequest.BodyPublishers.ofString(requestBody))
    				.build();
    		
    		logger.info("Sending request to GenAI.\n\n" + requestBody);
    		
    		try
    		{
    			HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    			
    			if (response.statusCode() != 200)
    			{
    			    logger.error("Request failed with status code: " + response.statusCode() + " and response body: " + new String(response.body().readAllBytes()));
    			}
    			try (var inputStream = response.body();
    			     var inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
    			     var reader = new BufferedReader(inputStreamReader)) 
    			{
    				String line;
    				while ((line = reader.readLine()) != null && !isCancelled.get() )
    				{
    					
    			     // Define the regular expression pattern
    			        Pattern pattern = Pattern.compile("\"generated_text\":\"(.*?)\"");

    			        // Create a Matcher object
    			        Matcher matcher = pattern.matcher(line);

						if (matcher.find()) {
							String value = matcher.group(1);
							System.out.print(value);
							String stringWithNewline = value.replace("\\n", "\n");
							publisher.submit(new Incoming(Incoming.Type.CONTENT, stringWithNewline));
						}
    				}
    			}
    			if ( isCancelled.get() )
    			{
    				publisher.closeExceptionally( new CancellationException() );
    			}
    		}
    		catch (Exception e)
    		{
    		    logger.error( e.getMessage(), e );
    			publisher.closeExceptionally(e);
    		} 
    		finally
    		{
    			publisher.close();
    		}
    	};
    }

}