package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.pose.Pose;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * An {@link IModel} adapter over a {@link BoneHierarchy}, letting the vanilla {@code ModelPart}
 * bone source drive the shared pose/bone editor toolchain (bone tree list, picker, pose editor)
 * without touching any shared class. MobForm renders through the vanilla entity renderer, never
 * through the cubic model pipeline, so the seven pipeline-only methods throw rather than silently
 * returning empty — an accidental call surfaces immediately instead of corrupting state.
 */
public final class VanillaModel implements IModel
{
    private final BoneHierarchy hierarchy;

    public VanillaModel(BoneHierarchy hierarchy)
    {
        this.hierarchy = hierarchy;
    }

    @Override
    public Collection<String> getRootGroupKeys()
    {
        return this.hierarchy.getRootIds();
    }

    @Override
    public Collection<String> getDirectChildrenKeys(String key)
    {
        return this.hierarchy.getDirectChildIds(key);
    }

    @Override
    public Collection<String> getAllGroupKeys()
    {
        return this.hierarchy.getBoneIds();
    }

    @Override
    public Collection<String> getAllChildrenKeys(String key)
    {
        return this.hierarchy.getDescendants(key).stream().map(BoneHierarchy.Bone::id).toList();
    }

    @Override
    public Collection<String> getAdjacentGroups(String groupName)
    {
        return this.hierarchy.getAdjacent(groupName).stream().map(BoneHierarchy.Bone::id).toList();
    }

    @Override
    public Collection<String> getHierarchyGroups(String groupName)
    {
        /* Model.getHierarchyGroups walks self → root; getAncestors yields root → self, so reverse. */
        List<String> groups = new ArrayList<>();

        for (BoneHierarchy.Bone ancestor : this.hierarchy.getAncestors(groupName))
        {
            groups.add(ancestor.id());
        }

        Collections.reverse(groups);

        return groups;
    }

    @Override
    public String getParentGroupKey(String key)
    {
        BoneHierarchy.Bone bone = this.hierarchy.getBone(key);

        return bone == null ? null : bone.parentId();
    }

    @Override
    public Set<String> getShapeKeys()
    {
        return Collections.emptySet();
    }

    @Override
    public String getAnchor()
    {
        return null;
    }

    @Override
    public Pose createPose()
    {
        throw new UnsupportedOperationException("VanillaModel has no cubic pose pipeline");
    }

    @Override
    public void resetPose()
    {
        throw new UnsupportedOperationException("VanillaModel has no cubic pose pipeline");
    }

    @Override
    public void applyPose(Pose pose)
    {
        throw new UnsupportedOperationException("VanillaModel has no cubic pose pipeline");
    }

    @Override
    public Collection<ModelGroup> getAllGroups()
    {
        throw new UnsupportedOperationException("VanillaModel has no cubic model groups");
    }

    @Override
    public Collection<BOBJBone> getAllBOBJBones()
    {
        throw new UnsupportedOperationException("VanillaModel has no BOBJ bones");
    }

    @Override
    public void apply(IEntity target, Animation action, float tick, float blend, float transition, boolean skipInitial)
    {
        throw new UnsupportedOperationException("VanillaModel animation is driven by the vanilla entity");
    }

    @Override
    public void postApply(IEntity target, Animation action, float tick, float transition)
    {
        throw new UnsupportedOperationException("VanillaModel animation is driven by the vanilla entity");
    }
}
