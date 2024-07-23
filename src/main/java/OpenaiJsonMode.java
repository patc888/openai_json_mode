import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatCompletionsJsonResponseFormat;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.core.credential.KeyCredential;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatTypes;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import com.fasterxml.jackson.module.jsonSchema.types.ArraySchema;

public class OpenaiJsonMode {
  static ObjectMapper mapper = new ObjectMapper();
  static Logger logger = Logger.getLogger(OpenaiJsonMode.class.getName());

  /*********************** openai chat ************************/

  /**
   * Sends a prompt to the OpenAI service and returns the results in POJOs.
   * It is assumed that the prompt contains instructions to return a JSON in the desired form.
   *
   * @param apikey The OpenAI API key.
   * @param model  The OpenAI model to use.
   * @param prompt The prompt to send to OpenAI for processing.
   * @param cls    The results will be returned in an instance of this class.
   * @param <T>
   * @return
   * @throws Exception
   */
  public static <T> T sendPrompt(String apikey, String model, String prompt, Class<T> cls) throws Exception {
    // Create the openai client
    var client = new OpenAIClientBuilder().credential(new KeyCredential(apikey)).buildClient();

    // Assemble the message and enable json mode
    var chatMessages = new ArrayList<ChatRequestMessage>();
    chatMessages.add(new ChatRequestSystemMessage(prompt));
    var options = new ChatCompletionsOptions(chatMessages);
    options.setResponseFormat(new ChatCompletionsJsonResponseFormat());
    options.setModel(model);

    // Call the OpenAI service
    var chatCompletions = client.getChatCompletions(options.getModel(), options);
    var usage = chatCompletions.getUsage();
    logger.fine(String.format(
        "tokens: %d (prompt=%d / completion=%d)%n",
        usage.getTotalTokens(), usage.getPromptTokens(), usage.getCompletionTokens()));
    var response = chatCompletions.getChoices().get(0).getMessage().getContent();
    logger.fine(prompt);
    logger.fine(response);

    // Now parse the results
    return mapper.readValue(response, cls);
  }

  /*********************** json schema ************************/

  /**
   * Returns the JSON schema of a POJO.
   * Note: By default, the returned JSON schema contains IDs for every object.
   * They tend to be long and when not needed, will cost tokens.
   * This method eliminates all unused IDs to reduce the size of the schema.
   *
   * @param cls The class for which the JSON schema to generate.
   * @return A string version of the JSON schema for cls.
   */
  public static String jsonSchemaOf(Class cls) throws Exception {
    JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(mapper);
    var schema = schemaGen.generateSchema(cls);
    var usedIds = new HashSet<String>();

    // First pass find all IDs that are used.
    findAllUsedIds(usedIds, schema);

    // Second pass remove all ununsed IDs.
    removeIds(usedIds, schema);
    return mapper.writeValueAsString(schema);
  }

  public static void findAllUsedIds(Set<String> usedIds, JsonSchema schema) {
    if (schema.get$ref() != null) {
      usedIds.add(schema.get$ref());
    }
    if (JsonFormatTypes.OBJECT.equals(schema.getType())) {
      if (schema.asObjectSchema() != null) {
        schema
            .asObjectSchema()
            .getProperties()
            .forEach((key, value) -> findAllUsedIds(usedIds, value));
      }
    } else if (JsonFormatTypes.ARRAY.equals(schema.getType())) {
      final ArraySchema.Items items = schema.asArraySchema().getItems();
      if (items.isArrayItems()) {
        Stream.of(items.asArrayItems().getJsonSchemas()).forEach(s -> findAllUsedIds(usedIds, s));
      } else {
        findAllUsedIds(usedIds, items.asSingleItems().getSchema());
      }
    }
  }

  public static void removeIds(Set<String> usedIds, JsonSchema schema) {
    if (!usedIds.contains(schema.getId())) {
      schema.setId(null);
    }
    if (JsonFormatTypes.OBJECT.equals(schema.getType())) {
      if (schema.asObjectSchema() != null) {
        schema.asObjectSchema().getProperties().forEach((key, value) -> removeIds(usedIds, value));
      }
    } else if (JsonFormatTypes.ARRAY.equals(schema.getType())) {
      final ArraySchema.Items items = schema.asArraySchema().getItems();
      if (items.isArrayItems()) {
        Stream.of(items.asArrayItems().getJsonSchemas()).forEach(s -> removeIds(usedIds, s));
      } else {
        removeIds(usedIds, items.asSingleItems().getSchema());
      }
    }
  }
}

