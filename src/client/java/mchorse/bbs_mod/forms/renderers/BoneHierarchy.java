package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.utils.pose.Pose;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A renderer-independent, string-only view of a form's editable bone hierarchy.
 *
 * <p><strong>ID stability contract:</strong> the bone ID rule ({@code layerId + "/" + path},
 * {@code layerId = modelResource + "#" + layer}) is frozen from this feature's first release.
 * Any ID-rule change must ship a migration path — this class carries none, so a migration, once
 * needed, can only live where saves are read.</p>
 */
public final class BoneHierarchy
{
    public static final BoneHierarchy EMPTY = new BoneHierarchy(Collections.emptyList());

    private final List<Bone> bones;
    private final List<String> boneIds;
    private final Map<String, Bone> bonesById;

    public BoneHierarchy(List<Bone> bones)
    {
        List<Bone> uniqueBones = new ArrayList<>(bones.size());
        List<String> boneIds = new ArrayList<>(bones.size());
        Map<String, Bone> bonesById = new LinkedHashMap<>();

        for (Bone bone : bones)
        {
            if (bone == null || bone.id() == null || bonesById.putIfAbsent(bone.id(), bone) != null)
            {
                continue;
            }

            uniqueBones.add(bone);
            boneIds.add(bone.id());
        }

        this.bones = Collections.unmodifiableList(uniqueBones);
        this.boneIds = Collections.unmodifiableList(boneIds);
        this.bonesById = Collections.unmodifiableMap(bonesById);
    }

    public List<Bone> getBones()
    {
        return this.bones;
    }

    public List<String> getBoneIds()
    {
        return this.boneIds;
    }

    public Bone getBone(String id)
    {
        return id == null ? null : this.bonesById.get(id);
    }

    /** The tree entry points — bones whose parent is null/empty, in registration order. */
    public List<String> getRootIds()
    {
        List<String> roots = new ArrayList<>();

        for (Bone bone : this.bones)
        {
            if (bone.parentId() == null || bone.parentId().isEmpty())
            {
                roots.add(bone.id());
            }
        }

        return roots;
    }

    /** Direct children of the given bone, in registration order. */
    public List<String> getDirectChildIds(String id)
    {
        List<String> children = new ArrayList<>();

        for (Bone bone : this.bones)
        {
            if (id.equals(bone.parentId()))
            {
                children.add(bone.id());
            }
        }

        return children;
    }

    /**
     * Builds stable editor labels while retaining mapping-independent IDs as list values. Main
     * model fields use lower camel case; feature model fields use a snake-case layer namespace and
     * retain lower camel case for the Java-style field suffix (for example inner_armor_rightArm).
     */
    public Map<String, String> getLabels(boolean indent)
    {
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, Integer> suffixes = new HashMap<>();
        Set<String> usedLabels = new HashSet<>();

        for (Bone bone : this.bones)
        {
            String label = getDisplayName(bone);

            if (usedLabels.contains(label))
            {
                label = this.getQualifiedName(bone);

                if (usedLabels.contains(label))
                {
                    label = getLayerResource(bone.layerId()) + "_" + label;
                }
            }

            label = makeUnique(label, suffixes, usedLabels);

            if (indent)
            {
                label = "  ".repeat(bone.depth()) + label;
            }

            labels.put(bone.id(), label);
        }

        return Collections.unmodifiableMap(labels);
    }

    /** The display label for one bone (see {@link #getLabels(boolean)}). */
    public String getLabel(String id)
    {
        return this.getLabels(false).get(id);
    }

    private static String makeUnique(String label, Map<String, Integer> suffixes, Set<String> usedLabels)
    {
        if (usedLabels.add(label))
        {
            return label;
        }

        int suffix = suffixes.getOrDefault(label, 2);
        String candidate;

        do
        {
            candidate = label + "_" + suffix;
            suffix++;
        }
        while (!usedLabels.add(candidate));

        suffixes.put(label, suffix);

        return candidate;
    }

    public List<Bone> getAdjacent(String id)
    {
        Bone selected = this.getBone(id);

        if (selected == null)
        {
            return Collections.emptyList();
        }

        List<Bone> adjacent = new ArrayList<>();

        for (Bone bone : this.bones)
        {
            if (bone.layerId().equals(selected.layerId()) && sameParent(bone.parentId(), selected.parentId()))
            {
                adjacent.add(bone);
            }
        }

        return adjacent;
    }

    /** Returns every descendant of the selected bone in hierarchy order, excluding itself. */
    public List<Bone> getDescendants(String id)
    {
        Bone selected = this.getBone(id);

        if (selected == null)
        {
            return Collections.emptyList();
        }

        id = selected.id();

        List<Bone> descendants = new ArrayList<>();

        for (Bone candidate : this.bones)
        {
            Bone parent = candidate.parentId() == null ? null : this.getBone(candidate.parentId());

            while (parent != null)
            {
                if (id.equals(parent.id()))
                {
                    descendants.add(candidate);
                    break;
                }

                parent = parent.parentId() == null ? null : this.getBone(parent.parentId());
            }
        }

        return descendants;
    }

    /** Returns the ancestry path from the root down to the selected bone. */
    public List<Bone> getAncestors(String id)
    {
        List<Bone> ancestors = new ArrayList<>();
        Bone bone = this.getBone(id);

        while (bone != null)
        {
            ancestors.add(bone);
            bone = bone.parentId() == null || bone.parentId().isEmpty() ? null : this.getBone(bone.parentId());
        }

        Collections.reverse(ancestors);

        return ancestors;
    }

    /**
     * Builds the left/right mirror map for pose flipping: each bone maps to its mirrored
     * counterpart — same layer, same hierarchy path with left/right names swapped. One pair
     * emits one entry (a {@code paired} set prevents A→B / B→A duplicates). Moved here from
     * the feature-branch {@code UIPoseEditor} so the shared editor stays free of this
     * vanilla-specific coupling.
     */
    public Map<String, String> buildFlippedParts()
    {
        Map<String, String> idsByPath = new LinkedHashMap<>();
        Map<String, String> flipped = new LinkedHashMap<>();
        LinkedHashSet<String> paired = new LinkedHashSet<>();

        for (Bone bone : this.bones)
        {
            idsByPath.put(this.hierarchyPathKey(bone, false), bone.id());
        }

        for (Bone bone : this.bones)
        {
            String path = this.hierarchyPathKey(bone, false);
            String mirroredPath = this.hierarchyPathKey(bone, true);
            String partner = idsByPath.get(mirroredPath);

            if (!path.equals(mirroredPath)
                && partner != null
                && !partner.equals(bone.id())
                && !paired.contains(bone.id())
                && !paired.contains(partner))
            {
                flipped.put(bone.id(), partner);
                paired.add(bone.id());
                paired.add(partner);
            }
        }

        return flipped;
    }

    private String hierarchyPathKey(Bone bone, boolean mirror)
    {
        StringBuilder key = new StringBuilder(bone.layerId());

        for (Bone ancestor : this.getAncestors(bone.id()))
        {
            String name = mirror ? Pose.getMirrorName(ancestor.name()) : ancestor.name();

            key.append('\u0000').append(name.length()).append(':').append(name);
        }

        return key.toString();
    }

    private static boolean sameParent(String a, String b)
    {
        return a == null ? b == null : a.equals(b);
    }

    private static String getDisplayName(Bone bone)
    {
        if (bone.layerId().isEmpty())
        {
            return bone.name();
        }

        String namespace = getLayerNamespace(bone);
        String name = VanillaBoneHierarchy.toCamelCase(bone.name());

        return combineNamespace(namespace, name);
    }

    private static String getLayerNamespace(Bone bone)
    {
        int separator = bone.layerId().indexOf('#');
        String namespace = separator < 0 ? "" : bone.layerId().substring(separator + 1);

        if (!namespace.equals("main"))
        {
            return namespace;
        }

        return bone.primary() ? "" : getLayerResource(bone.layerId());
    }

    private static String combineNamespace(String namespace, String name)
    {
        if (namespace.isEmpty())
        {
            return name;
        }

        return namespace.equals(name) || namespace.endsWith("_" + name) ? namespace : namespace + "_" + name;
    }

    private static String getLayerResource(String layerId)
    {
        int separator = layerId.indexOf('#');
        String resource = separator < 0 ? layerId : layerId.substring(0, separator);

        if (resource.startsWith("minecraft:"))
        {
            resource = resource.substring("minecraft:".length());
        }

        return resource.replace(':', '_').replace('/', '_');
    }

    private String getQualifiedName(Bone bone)
    {
        StringBuilder name = new StringBuilder();
        String namespace = getLayerNamespace(bone);

        if (!namespace.isEmpty())
        {
            name.append(namespace);
        }

        boolean first = true;

        for (Bone ancestor : this.getAncestors(bone.id()))
        {
            String segment = ancestor.layerId().isEmpty()
                ? ancestor.name()
                : VanillaBoneHierarchy.toCamelCase(ancestor.name());

            if (first && !name.isEmpty() && (name.toString().equals(segment) || name.toString().endsWith("_" + segment)))
            {
                first = false;
                continue;
            }

            if (!name.isEmpty())
            {
                name.append('_');
            }

            name.append(segment);
            first = false;
        }

        return name.toString();
    }

    public record Bone(String id, String name, String parentId, int depth, String layerId, boolean primary)
    {
        public Bone(String id, String name, String parentId, int depth, String layerId)
        {
            this(id, name, parentId, depth, layerId, true);
        }

        public Bone
        {
            name = name == null ? id : name;
            depth = Math.max(0, depth);
            layerId = layerId == null ? "" : layerId;
        }
    }
}
