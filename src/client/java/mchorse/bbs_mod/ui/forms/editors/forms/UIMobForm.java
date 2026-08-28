package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIMobFormPanel;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.StringUtils;
import org.joml.Matrix4f;

public class UIMobForm extends UIForm<MobForm>
{
    public UIMobFormPanel mobPanel;

    public UIMobForm()
    {
        super();

        this.mobPanel = new UIMobFormPanel(this);
        this.mobPanel.poseEditor.transform.hotkeyDrag(() -> this.editor == null ? null : this.editor.buildHotkeyDrag(this.mobPanel.poseEditor.transform));
        this.mobPanel.poseEditor.transform.worldTransform(new FormBoneWorldProvider(this, this.mobPanel.poseEditor));
        this.defaultPanel = this.mobPanel;

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_MOB_TITLE, Icons.MORPH);
        this.registerDefaultPanels();
    }

    @Override
    public UIPropTransform getEditableTransform()
    {
        return this.mobPanel.poseEditor.transform;
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        data.put("bones", DataStorageUtils.stringListToData(this.mobPanel.poseEditor.groups.list.getCurrent()));
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        if (data.has("bones"))
        {
            this.mobPanel.poseEditor.restoreSelection(DataStorageUtils.stringListFromData(data.get("bones")));
        }
    }

    @Override
    public Matrix4f getOrigin(float transition)
    {
        return this.getOrigin(transition, this.bonePath(), this.mobPanel.poseEditor.transform.isLocal());
    }

    @Override
    public Matrix4f getOriginMatrix(float transition)
    {
        return this.getOrigin(transition, this.bonePath(), true);
    }

    @Override
    public TransformSpace getGizmoSpace()
    {
        return this.mobPanel.poseEditor.transform.getSpace();
    }

    private String bonePath()
    {
        return StringUtils.combinePaths(FormUtils.getPath(this.form), this.mobPanel.poseEditor.groups.list.getCurrentFirst());
    }

    @Override
    public boolean toggleBoneSelection(String bone)
    {
        if (!this.mobPanel.poseEditor.hasBone(bone))
        {
            return false;
        }

        this.mobPanel.poseEditor.selectBone(bone, true);

        return true;
    }
}
