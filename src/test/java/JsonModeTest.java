import lombok.ToString;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class JsonModeTest {

  /**
   * The desired structure of the results from the LLM.
   */
  @ToString
  public static class Entries {
    @ToString
    public static class Entry {
      public String originalWord;
      public String wordInEnglish;
      public String pronunciation;
    }

    public List<Entry> entries;
  }

  /**
   * Test to get some stuctured output.
   */
  @Test
  public void test() throws Exception {
    var apikey = System.getenv("OPENAI_API_KEY");
    var sentence = "人工智慧將引領未來，以智慧之光照亮人類無限可能的前程。";

    // Construct the prompt with the input and output schema.
    var prompt = MessageFormat.format("""
        Parse the following sentence into English and return the results
        in JSON according to the following JSON schema.
        
        --- sentence ---
        {0}
        
        --- output json schema ---
        {1}
                
        """, sentence, OpenaiJsonMode.jsonSchemaOf(Entries.class));
    var result = OpenaiJsonMode.sendPrompt(apikey, "gpt-4o-mini", prompt, Entries.class);

    // Ensure that all the characters from the original sentence appear in the results.
    var charSet = sentence.chars().mapToObj(c -> (char) c).collect(Collectors.toSet());
    var seenSet = new HashSet<Character>();
    result.entries.stream().forEach(e->{
      for (char c : e.originalWord.toCharArray()) {
        Assert.assertTrue(charSet.contains(c));
        seenSet.add(c);
      }
    });
    Assert.assertNotEquals(charSet, seenSet);
  }
}
