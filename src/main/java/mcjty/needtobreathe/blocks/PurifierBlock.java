package mcjty.needtobreathe.blocks;

import mcjty.needtobreathe.NeedToBreathe;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.List;

public class PurifierBlock extends CommonPurifierBlock<PurifierTileEntity> {

    public PurifierBlock() {
        super("purifier", PurifierTileEntity.class);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack itemStack, World player, List<String> list, ITooltipFlag whatIsThis) {
        super.addInformation(itemStack, player, list, whatIsThis);

        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            list.add(TextFormatting.WHITE + "This machine cleans air");
            list.add(TextFormatting.WHITE + "using power and coal or charcoal");
            list.add(TextFormatting.WHITE + "Works best in closed area!");
        } else {
            list.add(TextFormatting.WHITE + NeedToBreathe.SHIFT_MESSAGE);
        }
    }
}
