package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flipkart.zjsonpatch.DiffFlags;
import com.flipkart.zjsonpatch.JsonDiff;
import com.flipkart.zjsonpatch.JsonPointer;
import com.flipkart.zjsonpatch.JsonPointer.RefToken;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public static void main(String[] args) throws IOException {

    File jsonFile1 = new File("src/main/resources/json1.json");
    File jsonFile2 = new File("src/main/resources/json2.json");

    ObjectNode jsonNode1 = (ObjectNode) objectMapper.readTree(jsonFile1);
    ObjectNode jsonNode2 = (ObjectNode) objectMapper.readTree(jsonFile2);

    File jsonDiffFile = createJsonDiffFile();

    Map<String, List<ObjectNode>> discountListWithKeyPath1 = removeAndGetDiscountListFromAssetList(
        (ArrayNode) jsonNode1.get("assetList"));

    Map<String, List<ObjectNode>> discountListWithKeyPath2 = removeAndGetDiscountListFromAssetList(
        (ArrayNode) jsonNode2.get("assetList"));

    Map<String, Pair<ObjectNode,ObjectNode>> jsonPathWithNameToDiscountPairMap = getJsonPathWithNameToDiscountPairMap(discountListWithKeyPath1,
        discountListWithKeyPath2);

    ArrayNode discountList1 = objectMapper.createArrayNode();
    ArrayNode discountList2 = objectMapper.createArrayNode();

    for (Map.Entry<String, Pair<ObjectNode, ObjectNode>> jsonPathWithNameToDiscountPairEntry : jsonPathWithNameToDiscountPairMap.entrySet()) {

      discountList1.add(jsonPathWithNameToDiscountPairEntry.getValue().getFirst());
      discountList2.add(jsonPathWithNameToDiscountPairEntry.getValue().getSecond());

    }

    jsonNode1.set("discountList", discountList1);
    jsonNode2.set("discountList", discountList2);

    ArrayNode jsonPatchDiffArray = (ArrayNode) JsonDiff.asJson(jsonNode1, jsonNode2, EnumSet.of(DiffFlags.ADD_ORIGINAL_VALUE_ON_REPLACE, DiffFlags.OMIT_COPY_OPERATION,
        DiffFlags.OMIT_MOVE_OPERATION));

    // we could transform jsonNode2 as well to diff response format
    transformJsonNode1ToDiffResponseFormat(jsonPatchDiffArray, jsonNode1, jsonNode2);

    writeStringToFile(jsonDiffFile, jsonNode1.toPrettyString());
  }

  private static void transformJsonNode1ToDiffResponseFormat(ArrayNode jsonDiffPatchArray, JsonNode jsonNode1, JsonNode jsonNode2) {
    for(int diffPatchIdx = 0; diffPatchIdx < jsonDiffPatchArray.size(); ++diffPatchIdx) {

      JsonNode current1 = jsonNode1;

      JsonNode jsonDiffPatch = jsonDiffPatchArray.get(diffPatchIdx);
      String path = jsonDiffPatch.get("path").textValue();
      JsonPointer jsonPointerFromPath = JsonPointer.parse(path);
      List<RefToken> pathTokens = jsonPointerFromPath.decompose();

      for (int tokenIdx = 0; tokenIdx < pathTokens.size()-1; ++tokenIdx) {
        final RefToken token = pathTokens.get(tokenIdx);

        if (current1.isArray()) {
          current1 = current1.get(token.getIndex());
        }
        else if (current1.isObject()) {
          current1 = current1.get(token.getField());
        }
      }

      String operation = jsonDiffPatch.get("op").textValue();

      String diffFieldActualName = pathTokens.get(pathTokens.size() - 1).getField();

      String diffFieldNameWithSuffix = diffFieldActualName + "_ch";

      if (operation.equals("add")) {

        JsonNode json1Value = jsonDiffPatch.get("value");

        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.set("value-1", json1Value);
        objectNode.set("value-2", objectMapper.createObjectNode());

        ((ObjectNode) current1).set(diffFieldNameWithSuffix, objectNode);

      } else if (operation.equals("remove")) {

        JsonNode json2Value = jsonDiffPatch.get("value");

        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.set("value-1", objectMapper.createObjectNode());
        objectNode.set("value-2", json2Value);

        ((ObjectNode) current1).set(diffFieldNameWithSuffix, objectNode);

      } else if (operation.equals("replace")) {

        JsonNode json1Value = jsonDiffPatch.get("value");
        JsonNode json2Value = jsonDiffPatch.get("fromValue");

        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.set("value-1", json1Value);
        objectNode.set("value-2", json2Value);

        ((ObjectNode) current1).set(diffFieldNameWithSuffix, objectNode);
      } else {
        throw new RuntimeException("Operations other than add, remove and replace are not supported");
      }

      ((ObjectNode) current1).remove(diffFieldActualName);
    }
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

            jsonPathWithNameToDiscountPairMap.put(jsonPathWithName, new Pair<>(discount, objectMapper.createObjectNode()));
          });

          discountList2.forEach(discount -> {
            String name = discount.get("name").textValue();
            String jsonPathWithName = discountListPath + "?name=" + name;

            if (jsonPathWithNameToDiscountPairMap.containsKey(jsonPathWithName)) {
              jsonPathWithNameToDiscountPairMap.get(jsonPathWithName).setSecond(discount);
            } else {
              jsonPathWithNameToDiscountPairMap.put(jsonPathWithName, new Pair<>(objectMapper.createObjectNode(), discount));
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

  private static void writeStringToFile(File file, String jsonDiffNode) throws IOException {
    FileWriter fileWriter = new FileWriter(file, true);
    BufferedWriter bufferedLogWriter = new BufferedWriter(fileWriter);

    bufferedLogWriter.write(jsonDiffNode);
    bufferedLogWriter.close();
  }

  private static File createJsonDiffFile() throws IOException {
    File jsonDiffFile = new File("src/main/resources/json-diff.json");
    jsonDiffFile.delete();

    jsonDiffFile.createNewFile();
    return jsonDiffFile;
  }

}
