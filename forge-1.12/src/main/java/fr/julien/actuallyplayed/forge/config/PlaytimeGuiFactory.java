package fr.julien.actuallyplayed.forge.config;

import fr.julien.actuallyplayed.forge.Reference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;

import java.util.Set;

/**
 * Wires the "Config" button of the mod list to the settings screen.
 */
public class PlaytimeGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraft) {
    }

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parent) {
        return new GuiConfig(
                parent,
                ForgeConfig.getConfigElements(),
                Reference.MOD_ID,
                false,
                false,
                Reference.MOD_NAME);
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }
}
