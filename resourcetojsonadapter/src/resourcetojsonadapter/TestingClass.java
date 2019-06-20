package resourcetojsonadapter;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

public class TestingClass
{
    static String flurble = "a;ljfkldjs";

    static double arbitrary = 213.12;

    static int[] bumble = new int[] { 2, 3, 4, 5 };

    public static void main(String[] args)
    {
        final JsonObjectBuilder objectbuilder = Json.createObjectBuilder();
        objectbuilder.add("stringvalue", flurble);
        objectbuilder.add("intvalue", arbitrary);
        objectbuilder.add("array", bumble.toString());
        JsonObject jsoninst = objectbuilder.build();
        System.out.println(jsoninst.toString());
        // import org.json.JSONObject;
    }
}
