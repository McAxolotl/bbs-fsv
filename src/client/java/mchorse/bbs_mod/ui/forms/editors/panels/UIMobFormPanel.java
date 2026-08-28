package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.renderers.BoneHierarchy;
import mchorse.bbs_mod.forms.renderers.MobFormRenderer;
import mchorse.bbs_mod.forms.renderers.VanillaModel;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIModelPoseEditor;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextarea;
import mchorse.bbs_mod.ui.framework.elements.input.text.utils.TextLine;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class UIMobFormPanel extends UIFormPanel<MobForm>
{
    private static final List<String> MOB_IDS = new ArrayList<>();

    public UIButton pickMob;
    public UIButton pick;
    public UIColor color;
    public UIToggle paused;
    public UIToggle slim;
    public UISection nbtSection;
    public UITextarea<TextLine> mobNBT;
    public UIModelPoseEditor poseEditor;

    static
    {
        for (RegistryKey<EntityType<?>> key : Registries.ENTITY_TYPE.getKeys())
        {
            MOB_IDS.add(key.getValue().toString());
        }

        /* The player entity type is never registered in the registry — the slim toggle
         * depends on mobID being selectable, so it is listed explicitly. */
        if (!MOB_IDS.contains("minecraft:player"))
        {
            MOB_IDS.add("minecraft:player");
        }

        MOB_IDS.sort(Comparator.naturalOrder());
    }

    public UIMobFormPanel(UIForm editor)
    {
        super(editor);

        this.pickMob = new UIButton(UIKeys.FORMS_EDITORS_MOB_PICK_MOB, (b) ->
        {
            UIListOverlayPanel list = new UIListOverlayPanel(UIKeys.FORMS_EDITORS_MOB_MOBS, (id) ->
            {
                this.form.mobID.set(id);
                this.editor.startEdit(this.form);
            });

            list.addValues(MOB_IDS);
            list.setValue(this.form.mobID.get());

            UIOverlay.addOverlay(this.getContext(), list);
        });
        this.pick = new UIButton(UIKeys.FORMS_EDITOR_MODEL_PICK_TEXTURE, (b) ->
        {
            Link link = this.form.texture.get();

            UITexturePicker.open(this.getContext(), link, (l) -> this.form.texture.set(l));
        });
        this.color = new UIColor((c) -> this.form.color.set(Color.rgba(c))).withAlpha();
        this.paused = new UIToggle(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_PAUSED, (b) -> this.form.paused.set(b.getValue()));
        this.slim = new UIToggle(UIKeys.FORMS_EDITOR_SLIM, (b) ->
        {
            this.form.slim.set(b.getValue());
        });
        this.slim.tooltip(UIKeys.FORMS_EDITOR_SLIM_TOOLTIP);

        this.mobNBT = new UITextarea<>((t) -> this.form.mobNBT.set(t));
        this.mobNBT.background().h(160);
        this.mobNBT.wrap();
        this.nbtSection = this.section(UIKeys.SELECTORS_NBT, "mob_nbt", false);
        this.nbtSection.fields.add(this.mobNBT);

        this.poseEditor = new UIModelPoseEditor();
        this.poseEditor.transform.barBackground();

        this.options.add(this.pickMob, this.pick, this.color, this.paused);
    }

    @Override
    public void startEdit(MobForm form)
    {
        super.startEdit(form);

        this.color.setColor(this.form.color.get().getARGBColor());
        this.paused.setValue(this.form.paused.get());
        this.mobNBT.setText(this.form.mobNBT.get());
        this.slim.setValue(this.form.slim.get());
        this.slim.removeFromParent();
        this.poseEditor.removeFromParent();
        this.nbtSection.removeFromParent();

        /* The slim toggle only applies to the player model — the tooltip already said so,
         * now the toggle follows: it appears only while a player mob is selected. */
        if (this.form.isPlayer())
        {
            this.options.add(this.slim);
        }

        this.options.add(this.poseEditor);
        /* NBT folds at the very bottom, under the bone editor. */
        this.options.add(this.nbtSection);
        this.options.resize();

        this.poseEditor.setValuePose(this.form.pose);
        this.poseEditor.setPose(this.form.pose.get(), "");

        BoneHierarchy hierarchy = ((MobFormRenderer) FormUtilsClient.getRenderer(this.form)).getBoneHierarchy();

        this.poseEditor.groups.list.labels(hierarchy::getLabel);
        this.poseEditor.fillGroups(new VanillaModel(hierarchy), hierarchy.buildFlippedParts(), true);
    }

    @Override
    public void pickBone(String bone)
    {
        super.pickBone(bone);

        if (bone != null && !bone.isEmpty())
        {
            this.poseEditor.selectBone(bone);
        }
    }
}
