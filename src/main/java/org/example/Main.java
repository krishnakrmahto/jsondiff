package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.DiffFlags;
import com.flipkart.zjsonpatch.JsonDiff;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.EnumSet;
import java.util.stream.StreamSupport;

public class Main {

  public static void main(String[] args) throws IOException {

    File jsonFile1 = new File("src/main/resources/json1.txt");
    File jsonFile2 = new File("src/main/resources/json2.txt");

    JsonNode jsonDiff = getJsonDiff(jsonFile1, jsonFile2);


    File jsonDiffFile = new File("src/main/resources/json-diff.txt");
    jsonDiffFile.delete();

    jsonDiffFile.createNewFile();

    StreamSupport.stream(jsonDiff.spliterator(), false)
        .forEach(jsonDiffNode -> {

          String completePrintString = getDiffKeyValuePair(jsonDiffNode);

          try {

            FileWriter fileWriter = new FileWriter(jsonDiffFile, true);
            BufferedWriter bw = new BufferedWriter(fileWriter);

            bw.write(completePrintString);
            bw.close();

          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static String getDiffKeyValuePair(JsonNode jsonDiffNode) {

    String operation = jsonDiffNode.get("op").textValue();

    String printableDiffText = null;
    String keyPath = jsonDiffNode.get("path").textValue();

    if (operation.equals("add")) {

      String json1Value = jsonDiffNode.get("value").textValue();

      printableDiffText = "Key path: " + keyPath + "\n" + "Value-1: " + json1Value + "\n" + "Value-2: " + null;

    } else if (operation.equals("remove")) {

      String json2Value = jsonDiffNode.get("value").textValue();

      printableDiffText = "Key path: " + keyPath + "\n" + "Value-1: " + null + "\n" + "Value-2: " + json2Value;

    } else if (operation.equals("replace")) {

      String json1Value = jsonDiffNode.get("value").textValue();
      String json2Value = jsonDiffNode.get("fromValue").textValue();

      printableDiffText = "Key path: " + keyPath + "\n" + "Value-1: " + json1Value + "\n" +  "Value-2: " + json2Value;
    }

    return printableDiffText + "\n\n";
  }

  private static JsonNode getJsonDiff(File jsonFile1, File jsonFile2)
      throws IOException {

    ObjectMapper objectMapper = new ObjectMapper();

    JsonNode jsonNode1 = objectMapper.readTree(jsonFile1);
    JsonNode jsonNode2 = objectMapper.readTree(jsonFile2);

    return JsonDiff.asJson(jsonNode1, jsonNode2, EnumSet.of(DiffFlags.ADD_ORIGINAL_VALUE_ON_REPLACE));
  }
}
