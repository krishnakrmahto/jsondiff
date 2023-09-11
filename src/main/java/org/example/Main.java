package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonDiff;
import java.io.File;
import java.io.IOException;
import java.util.stream.StreamSupport;

public class Main {

  public static void main(String[] args) throws IOException {

    ObjectMapper objectMapper = new ObjectMapper();

    File jsonFile1 = new File("src/main/resources/json1.txt");
    File jsonFile2 = new File("src/main/resources/json2.txt");

    JsonNode jsonNode1 = objectMapper.readTree(jsonFile1);
    JsonNode jsonNode2 = objectMapper.readTree(jsonFile2);

    JsonNode jsonDiff = JsonDiff.asJson(jsonNode1, jsonNode2);

    System.out.println();
    System.out.println("Difference (path-value pairs): ");
    StreamSupport.stream(jsonDiff.spliterator(), false)
        .forEach(jsonDiffNode -> {
          System.out.println("Path: " + jsonDiffNode.get("path").toString().replace("/", ""));
          System.out.println("Value: " + jsonDiffNode.get("value"));
        });

    System.out.println();
  }
}
