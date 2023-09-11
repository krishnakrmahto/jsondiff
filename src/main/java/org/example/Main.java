package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonDiff;
import java.util.Scanner;
import java.util.stream.StreamSupport;

public class Main {

  public static void main(String[] args) throws JsonProcessingException {
    Scanner sc = new Scanner(System.in);

    while (true) {
      System.out.println("Enter first json: ");
      String json1 = sc.nextLine();

      System.out.println("Enter second json: ");
      String json2 = sc.nextLine();

      ObjectMapper objectMapper = new ObjectMapper();

      JsonNode jsonNode1 = objectMapper.readTree(json1);
      JsonNode jsonNode2 = objectMapper.readTree(json2);

      JsonNode jsonDiff = JsonDiff.asJson(jsonNode1, jsonNode2);

      System.out.println();
      System.out.println("Difference (path-value pairs): ");
      StreamSupport.stream(jsonDiff.spliterator(), false)
          .forEach(jsonDiffNode -> {
            System.out.println("Path: " + jsonDiffNode.get("path").toString().replace("/",""));
            System.out.println("Value: " + jsonDiffNode.get("value"));
          });

      System.out.println();
    }
  }
}
