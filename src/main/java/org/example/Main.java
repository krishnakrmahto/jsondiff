package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flipkart.zjsonpatch.DiffFlags;
import com.flipkart.zjsonpatch.JsonDiff;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

public class Main {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public static void main(String[] args) throws IOException {

    File jsonFile1 = new File("src/main/resources/json1.json");
    File jsonFile2 = new File("src/main/resources/json2.json");

    JsonNode jsonNode1 = objectMapper.readTree(jsonFile1);
    JsonNode jsonNode2 = objectMapper.readTree(jsonFile2);

    File jsonDiffFile = createJsonDiffFile();
    File logFile = createLogFile();

    Map<String, List<ObjectNode>> discountListWithKeyPath1 = removeAndGetDiscountListFromAssetList(
        (ArrayNode) jsonNode1.get("assetList"));

    Map<String, List<ObjectNode>> discountListWithKeyPath2 = removeAndGetDiscountListFromAssetList(
        (ArrayNode) jsonNode2.get("assetList"));

    Map<String, Pair<ObjectNode,ObjectNode>> jsonPathWithNameToDiscountPairMap = getJsonPathWithNameToDiscountPairMap(discountListWithKeyPath1,
        discountListWithKeyPath2);

    writeJsonDiffWithoutDiscountList(jsonNode1, jsonNode2, jsonDiffFile, logFile);

    writeJsonDiffForDiscountList(jsonPathWithNameToDiscountPairMap, jsonDiffFile);

  }

  private static Map<String, Pair<ObjectNode, ObjectNode>> getJsonPathWithNameToDiscountPairMap(
      Map<String, List<ObjectNode>> discountListWithKeyPath1, Map<String, List<ObjectNode>> discountListWithKeyPath2) {

    Set<String> discountListPaths = discountListWithKeyPath1.keySet();

    Map<String, Pair<ObjectNode, ObjectNode>> jsonPathWithNameToDiscountPairMap = new HashMap<>();

    discountListPaths
        .forEach(discountListPath -> {
          List<ObjectNode> discountList1 = discountListWithKeyPath1.get(discountListPath);
          List<ObjectNode> discountList2 = discountListWithKeyPath2.get(discountListPath);

          discountList1.forEach(discount -> {
            String name = discount.get("name").textValue();
            String jsonPathWithName = discountListPath + "?name=" + name;

            jsonPathWithNameToDiscountPairMap.put(jsonPathWithName, new Pair<>(discount, null));
          });

          discountList2.forEach(discount -> {
            String name = discount.get("name").textValue();
            String jsonPathWithName = discountListPath + "?name=" + name;

            if (jsonPathWithNameToDiscountPairMap.containsKey(jsonPathWithName)) {
              jsonPathWithNameToDiscountPairMap.get(jsonPathWithName).setSecond(discount);
            } else {
              jsonPathWithNameToDiscountPairMap.put(jsonPathWithName, new Pair<>(null, discount));
            }
          });
        });

    return jsonPathWithNameToDiscountPairMap;
  }

  private static Map<String, List<ObjectNode>> removeAndGetDiscountListFromAssetList(ArrayNode assetList) {

    Function<Integer, String> keyMapper = index ->  "/assetList" + "/" + index + "/discountList";
    Function<Integer, List<ObjectNode>> valueMapper = index -> {
      List<ObjectNode> value = new ArrayList<>();
      ((ObjectNode) assetList.get(index)).remove("discountList").forEach(discount -> value.add((ObjectNode) discount));
      return value;
    };

    return IntStream.range(0, assetList.size())
        .boxed()
        .collect(Collectors.toMap(keyMapper, valueMapper));
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

  private static String getPrintableDiffKeyValuePair(JsonNode jsonDiffNode, String pathPrefix) {

    String operation = jsonDiffNode.get("op").textValue();

    String printableDiffText;
    String keyPath = jsonDiffNode.get("path").textValue();
    keyPath = (Optional.ofNullable(pathPrefix).isPresent()? pathPrefix: "") + keyPath;

    if (operation.equals("add")) {

      String json1Value = jsonDiffNode.get("value").toString();

      printableDiffText = getPrintableDiffText(keyPath, json1Value, null);

    } else if (operation.equals("remove")) {

      String json2Value = jsonDiffNode.get("value").toString();

      printableDiffText = getPrintableDiffText(keyPath, null, json2Value);

    } else if (operation.equals("replace")) {

      String json1Value = jsonDiffNode.get("value").toString();
      String json2Value = jsonDiffNode.get("fromValue").toString();

      printableDiffText = getPrintableDiffText(keyPath, json1Value, json2Value);
    } else {
      printableDiffText = "Key path: " + keyPath;
    }

    return printableDiffText + "\n\n";
  }

  private static void writeJsonDiffWithoutDiscountList(JsonNode jsonNode1, JsonNode jsonNode2, File jsonDiffFile,
      File logFile) {
    JsonNode jsonDiffArray = getJsonDiff(jsonNode1, jsonNode2);

    StreamSupport.stream(jsonDiffArray.spliterator(), false)
        .forEach(jsonDiffNode -> {

          String jsonDiffPrintString = getPrintableDiffKeyValuePair(jsonDiffNode, null);

          try {

            writeStringToFile(logFile, jsonDiffNode + "\n\n");
            writeStringToFile(jsonDiffFile, jsonDiffPrintString);

          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static void writeJsonDiffForDiscountList(Map<String, Pair<ObjectNode, ObjectNode>> jsonPathWithNameToDiscountPairMap,
      File jsonDiffFile) {

    jsonPathWithNameToDiscountPairMap.forEach((jsonPathWithName, discountPair) -> {

      ObjectNode first = discountPair.getFirst();
      ObjectNode second = discountPair.getSecond();

      JsonNode jsonDiffArray = getJsonDiff(first, second);

      StreamSupport.stream(jsonDiffArray.spliterator(), false)
          .forEach(jsonDiffNode -> {

            String jsonDiffPrintString = getPrintableDiffKeyValuePair(jsonDiffNode, jsonPathWithName);

            try {

              writeStringToFile(jsonDiffFile, jsonDiffPrintString);

            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          });

    });
  }

  private static String getPrintableDiffText(String keyPath, String json1Value, String json2Value) {
    return "Key path: " + keyPath + "\n" + "Value-1: " + json1Value + "\n" + "Value-2: " + json2Value;
  }

  private static ObjectNode getDiffJson(String keyPath, String json1Value, String json2Value) {

    ObjectNode keyAsJsonObject = convertKeyPathToJsonObjectNode(keyPath);

    keyAsJsonObject.put("value-1", json1Value);
    keyAsJsonObject.put("value-2", json2Value);

    return keyAsJsonObject;
  }

  private static ObjectNode convertKeyPathToJsonObjectNode(String keyPath) {

    String[] jsonNodeSegments = keyPath.split("/");

    ObjectNode objectNode = objectMapper.createObjectNode();
    Arrays.stream(jsonNodeSegments).forEach(objectNode::putObject);

    return objectNode;
  }

  private static JsonNode getJsonDiff(JsonNode jsonNode1, JsonNode jsonNode2) {

    return JsonDiff.asJson(jsonNode1, jsonNode2,
        EnumSet.of(DiffFlags.ADD_ORIGINAL_VALUE_ON_REPLACE, DiffFlags.OMIT_COPY_OPERATION,
            DiffFlags.OMIT_MOVE_OPERATION));
  }
}
