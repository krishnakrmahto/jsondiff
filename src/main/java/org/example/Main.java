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

    JsonNode jsonDiffArray = getJsonDiff(jsonFile1, jsonFile2);

    File jsonDiffFile = createJsonDiffFile();

    File logFile = createLogFile();

    StreamSupport.stream(jsonDiffArray.spliterator(), false)
        .forEach(jsonDiffNode -> {

          String jsonDiffPrintString = getDiffKeyValuePair(jsonDiffNode);

          try {

            writeStringToFile(logFile, jsonDiffNode + "\n\n");
            writeStringToFile(jsonDiffFile, jsonDiffPrintString);

          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static void writeStringToFile(File logFile, String jsonDiffNode) throws IOException {
    FileWriter logWriter = new FileWriter(logFile, true);
    BufferedWriter bufferedLogWriter = new BufferedWriter(logWriter);

    bufferedLogWriter.write(jsonDiffNode);
    bufferedLogWriter.close();
  }

  private static File createLogFile() throws IOException {
    File logFile = new File("src/main/resources/json-diff-logs.txt");
    logFile.delete();

    logFile.createNewFile();
    return logFile;
  }

  private static File createJsonDiffFile() throws IOException {
    File jsonDiffFile = new File("src/main/resources/json-diff.txt");
    jsonDiffFile.delete();

    jsonDiffFile.createNewFile();
    return jsonDiffFile;
  }

  private static String getDiffKeyValuePair(JsonNode jsonDiffNode) {

    String operation = jsonDiffNode.get("op").textValue();

    String printableDiffText = null;
    String keyPath = jsonDiffNode.get("path").textValue();

    if (operation.equals("add")) {

      String json1Value = jsonDiffNode.get("value").toString();

      printableDiffText = "Key path: " + keyPath + "\n" + "Value-1: " + json1Value + "\n" + "Value-2: " + null;

    } else if (operation.equals("remove")) {

      String json2Value = jsonDiffNode.get("value").toString();

      printableDiffText = "Key path: " + keyPath + "\n" + "Value-1: " + null + "\n" + "Value-2: " + json2Value;

    } else if (operation.equals("replace")) {

      String json1Value = jsonDiffNode.get("value").toString();
      String json2Value = jsonDiffNode.get("fromValue").toString();

      printableDiffText = "Key path: " + keyPath + "\n" + "Value-1: " + json1Value + "\n" +  "Value-2: " + json2Value;
    } else {
      printableDiffText = "Key path: " + keyPath;
    }

    return printableDiffText + "\n\n";
  }

  private static JsonNode getJsonDiff(File jsonFile1, File jsonFile2)
      throws IOException {

    ObjectMapper objectMapper = new ObjectMapper();

    JsonNode jsonNode1 = objectMapper.readTree(jsonFile1);
    JsonNode jsonNode2 = objectMapper.readTree(jsonFile2);

    return JsonDiff.asJson(jsonNode1, jsonNode2,
        EnumSet.of(DiffFlags.ADD_ORIGINAL_VALUE_ON_REPLACE, DiffFlags.OMIT_COPY_OPERATION,
            DiffFlags.OMIT_MOVE_OPERATION));
  }
}
