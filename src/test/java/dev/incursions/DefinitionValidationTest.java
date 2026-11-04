package dev.incursions;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DefinitionValidationTest {
 @Test void rejectsNanAndInfinity(){for(String value:new String[]{"NaN","Infinity","-Infinity"}){var j=new JsonObject();j.addProperty("chance",value);assertThrows(IllegalArgumentException.class,()->Definitions.num(j,"chance",0,1));}}
 @Test void rejectsOutOfBoundsAndFractionalTicks(){var j=JsonParser.parseString("{\"chance\":1.1,\"ticks\":2.5}").getAsJsonObject();assertThrows(IllegalArgumentException.class,()->Definitions.num(j,"chance",0,1));assertThrows(IllegalArgumentException.class,()->Definitions.integer(j,"ticks",1,10));}
 @Test void missingFieldsFailValidation(){assertThrows(Exception.class,()->Definitions.num(new JsonObject(),"missing",0,1));}
 @Test void idMustBeNamespaced(){assertThrows(IllegalArgumentException.class,()->Definitions.id("piglin"));assertThrows(Exception.class,()->Definitions.id("Not Valid:ID"));assertEquals("minecraft:piglin",Definitions.id("minecraft:piglin").toString());}

 @Test void validatesIntervalsAndKeepsLegacyPacksCompatible(){
  assertEquals(20,Definitions.interval(JsonParser.parseString("{\"window_seconds\":4}").getAsJsonObject()));
  assertEquals(40,Definitions.interval(JsonParser.parseString("{\"window_seconds\":8,\"spawn_interval_ticks\":40}").getAsJsonObject()));
  for(int n:new int[]{0,10,30,200}){var j=new JsonObject();j.addProperty("window_seconds",8);j.addProperty("spawn_interval_ticks",n);assertThrows(IllegalArgumentException.class,()->Definitions.interval(j));}
 }
}
