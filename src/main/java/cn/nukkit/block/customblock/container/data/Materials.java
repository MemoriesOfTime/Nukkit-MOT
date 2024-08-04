package cn.nukkit.block.customblock.container.data;

import cn.nukkit.nbt.tag.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * 用于将方块的face(面)映射到实际的材质实例,并且设置渲染方法和参数
 * <p>
 * Used to map the face of a block to a material instance, and set the rendering method and parameters.
 */
public class Materials implements NBTData {
    private final CompoundTag tag;

    private Materials() {
        this.tag = new CompoundTag();
    }

    /**
     * Builder materials.
     *
     * @return the materials
     */
    public static Materials builder() {
        return new Materials();
    }

    /**
     * ambientOcclusion=true, faceDimming=true
     *
     * @see #up(RenderMethod, boolean, boolean, String)
     */
    public Materials up(RenderMethod renderMethod, String texture) {
        this.process("up", true, true, renderMethod, texture);
        return this;
    }

    /**
     * 指定up面对应的渲染方法、渲染参数和材质。
     * <p>
     * Specify the corresponding rendering method, rendering parameters and texture's name for the up face.
     *
     * @param renderMethod     要使用的渲染方法<br>Rendering method to be used
     * @param texture          指定up方向的材质名称<br>Specify the texture's name of the up face
     * @param ambientOcclusion 在照明时是否应该应用环境光遮蔽?<br>Should I apply ambient light shielding when lighting?
     * @param faceDimming      是否应该根据它所面对的方向变暗?<br>Should it be dimmed according to the direction it is facing?
     * @return the materials
     */
    public Materials up(RenderMethod renderMethod, boolean ambientOcclusion, boolean faceDimming, String texture) {
        this.process("up", ambientOcclusion, faceDimming, renderMethod, texture);
        return this;
    }

    /**
     * ambientOcclusion=true, faceDimming=true
     *
     * @see #down(RenderMethod, boolean, boolean, String)
     */
    public Materials down(RenderMethod renderMethod, String texture) {
        this.process("down", true, true, renderMethod, texture);
        return this;
    }

    /**
     * 指定down面对应的渲染方法、渲染参数和材质。
     * <p>
     * Specify the corresponding rendering method, rendering parameters and texture's name for the down face.
     *
     * @param renderMethod     要使用的渲染方法<br>Rendering method to be used
     * @param texture          指定down方向的材质名称<br>Specify the texture's name of the down face
     * @param ambientOcclusion 在照明时是否应该应用环境光遮蔽?<br>Should I apply ambient light shielding when lighting?
     * @param faceDimming      是否应该根据它所面对的方向变暗?<br>Should it be dimmed according to the direction it is facing?
     * @return the materials
     */
    public Materials down(RenderMethod renderMethod, boolean ambientOcclusion, boolean faceDimming, String texture) {
        this.process("down", ambientOcclusion, faceDimming, renderMethod, texture);
        return this;
    }

    /**
     * ambientOcclusion=true, faceDimming=true
     *
     * @see #north(RenderMethod, boolean, boolean, String)
     */
    public Materials north(RenderMethod renderMethod, String texture) {
        this.process("north", true, true, renderMethod, texture);
        return this;
    }

    /**
     * 指定north面对应的渲染方法、渲染参数和材质。
     * <p>
     * Specify the corresponding rendering method, rendering parameters and texture's name for the north face.
     *
     * @param renderMethod     要使用的渲染方法<br>Rendering method to be used
     * @param texture          指定north方向的材质名称<br>Specify the texture's name of the north face
     * @param ambientOcclusion 在照明时是否应该应用环境光遮蔽?<br>Should I apply ambient light shielding when lighting?
     * @param faceDimming      是否应该根据它所面对的方向变暗?<br>Should it be dimmed according to the direction it is facing?
     * @return the materials
     */
    public Materials north(RenderMethod renderMethod, boolean ambientOcclusion, boolean faceDimming, String texture) {
        this.process("north", ambientOcclusion, faceDimming, renderMethod, texture);
        return this;
    }

    /**
     * ambientOcclusion=true, faceDimming=true
     *
     * @see #south(RenderMethod, boolean, boolean, String)
     */
    public Materials south(RenderMethod renderMethod, String texture) {
        this.process("south", true, true, renderMethod, texture);
        return this;
    }

    /**
     * 指定south面对应的渲染方法、渲染参数和材质。
     * <p>
     * Specify the corresponding rendering method, rendering parameters and texture's name for the south face.
     *
     * @param renderMethod     要使用的渲染方法<br>Rendering method to be used
     * @param texture          指定south方向的材质名称<br>Specify the texture's name of the south face
     * @param ambientOcclusion 在照明时是否应该应用环境光遮蔽?<br>Should I apply ambient light shielding when lighting?
     * @param faceDimming      是否应该根据它所面对的方向变暗?<br>Should it be dimmed according to the direction it is facing?
     * @return the materials
     */
    public Materials south(RenderMethod renderMethod, boolean ambientOcclusion, boolean faceDimming, String texture) {
        this.process("south", ambientOcclusion, faceDimming, renderMethod, texture);
        return this;
    }

    /**
     * ambientOcclusion=true, faceDimming=true
     *
     * @see #east(RenderMethod, boolean, boolean, String)
     */
    public Materials east(RenderMethod renderMethod, String texture) {
        this.process("east", true, true, renderMethod, texture);
        return this;
    }

    /**
     * 指定east面对应的渲染方法、渲染参数和材质。
     * <p>
     * Specify the corresponding rendering method, rendering parameters and texture's name for the east face.
     *
     * @param renderMethod     要使用的渲染方法<br>Rendering method to be used
     * @param texture          指定east方向的材质名称<br>Specify the texture's name of the east face
     * @param ambientOcclusion 在照明时是否应该应用环境光遮蔽?<br>Should I apply ambient light shielding when lighting?
     * @param faceDimming      是否应该根据它所面对的方向变暗?<br>Should it be dimmed according to the direction it is facing?
     * @return the materials
     */
    public Materials east(RenderMethod renderMethod, boolean ambientOcclusion, boolean faceDimming, String texture) {
        this.process("east", ambientOcclusion, faceDimming, renderMethod, texture);
        return this;
    }

    /**
     * ambientOcclusion=true, faceDimming=true
     *
     * @see #west(RenderMethod, boolean, boolean, String)
     */
    public Materials west(RenderMethod renderMethod, String texture) {
        this.process("west", true, true, renderMethod, texture);
        return this;
    }

    /**
     * 指定west面对应的渲染方法、渲染参数和材质。
     * <p>
     * Specify the corresponding rendering method, rendering parameters and texture's name for the west face.
     *
     * @param renderMethod     要使用的渲染方法<br>Rendering method to be used
     * @param texture          指定west方向的材质名称<br>Specify the texture's name of the west face
     * @param ambientOcclusion 在照明时是否应该应用环境光遮蔽?<br>Should I apply ambient light shielding when lighting?
     * @param faceDimming      是否应该根据它所面对的方向变暗?<br>Should it be dimmed according to the direction it is facing?
     * @return the materials
     */
    public Materials west(RenderMethod renderMethod, boolean ambientOcclusion, boolean faceDimming, String texture) {
        this.process("west", ambientOcclusion, faceDimming, renderMethod, texture);
        return this;
    }

    /**
     * ambientOcclusion=true, faceDimming=true
     *
     * @see #any(RenderMethod, boolean, boolean, String)
     */
    public Materials any(RenderMethod renderMethod, String texture) {
        this.process("*", true, true, renderMethod, texture);
        return this;
    }

    /**
     * 指定所有面对应的渲染方法、渲染参数和材质。
     * <p>
     * Specify all corresponding rendering method, rendering parameters and texture name.
     *
     * @param renderMethod     要使用的渲染方法<br>Rendering method to be used
     * @param texture          材质名称<br>Specify the texture's name
     * @param ambientOcclusion 在照明时是否应该应用环境光遮蔽?<br>Should I apply ambient light shielding when lighting?
     * @param faceDimming      是否应该根据它所面对的方向变暗?<br>Should it be dimmed according to the direction it is facing?
     * @return the materials
     */
    public Materials any(RenderMethod renderMethod, boolean ambientOcclusion, boolean faceDimming, String texture) {
        this.process("*", ambientOcclusion, faceDimming, renderMethod, texture);
        return this;
    }

    /**
     * 指定对应对应的渲染方法、渲染参数和材质。此方法是完全自定义的，请在使用之前抓包确认参数合法性
     *
     * @param face             指定面的名称，可选值为：up, down, north, south, east, west, *
     * @param ambientOcclusion 在照明时是否应该应用环境光遮蔽?<br>Should it be applied ambient light shielding when lighting?
     * @param faceDimming      是否应该根据它所面对的方向变暗?<br>Should it be dimmed according to the direction it is facing?
     * @param renderMethodName 要使用的渲染方法<br>Rendering method to be used
     * @param texture          材质名称<br>Specify the texture's name
     */
    public void process(@NotNull String face, boolean ambientOcclusion, boolean faceDimming, @NotNull String renderMethodName, @NotNull String texture) {
        this.tag.putCompound(face, new CompoundTag()
                .putBoolean("ambient_occlusion", ambientOcclusion)
                .putBoolean("face_dimming", faceDimming)
                .putString("render_method", renderMethodName)
                .putString("texture", texture));
    }

    private void process(@NotNull String face, boolean ambientOcclusion, boolean faceDimming, @NotNull RenderMethod renderMethod, @NotNull String texture) {
        this.tag.putCompound(face, new CompoundTag()
                .putBoolean("ambient_occlusion", ambientOcclusion)
                .putBoolean("face_dimming", faceDimming)
                .putString("render_method", renderMethod.name().toLowerCase(Locale.ENGLISH))
                .putString("texture", texture));
    }

    public CompoundTag toCompoundTag() {
        return tag;
    }

    /**
     * 渲染方法枚举
     * <p>
     * The enum Render method.
     *
     * @see <a href="https://wiki.bedrock.dev/blocks/blocks-16.html#additional-notes">wiki.bedrock.dev</a>
     */
    public enum RenderMethod {
        OPAQUE,
        ALPHA_TEST,
        BLEND,
        DOUBLE_SIDED
    }
}
