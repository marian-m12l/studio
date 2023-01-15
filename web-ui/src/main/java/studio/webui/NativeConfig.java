package studio.webui;

import javax.enterprise.context.ApplicationScoped;
import io.quarkus.runtime.annotations.RegisterForReflection;
import studio.core.v1.model.ActionNode;
import studio.core.v1.model.ControlSettings;
import studio.core.v1.model.Node;
import studio.core.v1.model.StageNode;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.Transition;
import studio.core.v1.model.asset.MediaAsset;
import studio.core.v1.model.enriched.EnrichedNodeMetadata;
import studio.core.v1.model.enriched.EnrichedNodePosition;
import studio.core.v1.model.enriched.EnrichedNodeType;
import studio.core.v1.model.enriched.EnrichedPackMetadata;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.webui.model.EvergreenDTOs;

@RegisterForReflection(targets = {
        // studio jackson beans
        StoryPackMetadata.class,
        StoryPack.class,
        MediaAsset.class,
        Node.class,
        StageNode.class,
        Transition.class,
        ControlSettings.class,
        ActionNode.class,
        EnrichedNodeMetadata.class,
        EnrichedNodePosition.class,
        EnrichedNodeType.class,
        EnrichedPackMetadata.class,
        EvergreenDTOs.CommitDto.class //
})
@ApplicationScoped
public class NativeConfig {
}
