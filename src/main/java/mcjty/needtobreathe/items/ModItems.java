package mcjty.needtobreathe.items;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ModItems {

    public static ProtectiveHelmet protectiveHelmet;
    public static ProtectiveHelmet2 protectiveHelmet2;
    public static InformationGlasses informationGlasses;
    public static HazmatSuit hazmatSuitHelmet;
    public static HazmatSuit hazmatSuitChest;
    public static HazmatSuit hazmatSuitLegs;
    public static HazmatSuit hazmatSuitBoots;

    public static void init() {
        protectiveHelmet = new ProtectiveHelmet();
        protectiveHelmet2 = new ProtectiveHelmet2();
        informationGlasses = new InformationGlasses();
        hazmatSuitBoots = new HazmatSuit(EntityEquipmentSlot.FEET);
        hazmatSuitChest = new HazmatSuit(EntityEquipmentSlot.CHEST);
        hazmatSuitHelmet = new HazmatSuit(EntityEquipmentSlot.HEAD);
        hazmatSuitLegs = new HazmatSuit(EntityEquipmentSlot.LEGS);
    }

    @SideOnly(Side.CLIENT)
    public static void initModels() {
        ModelLoader.setCustomModelResourceLocation(protectiveHelmet, 0, new ModelResourceLocation(protectiveHelmet.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(protectiveHelmet2, 0, new ModelResourceLocation(protectiveHelmet2.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(informationGlasses, 0, new ModelResourceLocation(informationGlasses.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(hazmatSuitBoots, 0, new ModelResourceLocation(hazmatSuitBoots.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(hazmatSuitChest, 0, new ModelResourceLocation(hazmatSuitChest.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(hazmatSuitHelmet, 0, new ModelResourceLocation(hazmatSuitHelmet.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(hazmatSuitLegs, 0, new ModelResourceLocation(hazmatSuitLegs.getRegistryName(), "inventory"));
        
    }
}
