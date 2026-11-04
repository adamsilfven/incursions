package dev.incursions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SettingsPermissionTest {
    @Test void survivalOwnerCanEdit() {assertTrue(SettingsNetwork.mayEdit(true,false));}
    @Test void operatorCanEdit() {assertTrue(SettingsNetwork.mayEdit(false,true));}
    @Test void ordinaryRemotePlayerCannotEdit() {assertFalse(SettingsNetwork.mayEdit(false,false));}
}
