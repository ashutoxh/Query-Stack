package edu.neu.info7255.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.neu.info7255.model.PlanDocument;
import edu.neu.info7255.model.PlanIndexMessage;
import edu.neu.info7255.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.elasticsearch.core.join.JoinField;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PlanIndexListener {

    private final PlanRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    @RabbitListener(queues = "plan-queue")
    public void onMessage(PlanIndexMessage msg) throws IOException {
        switch (msg.getOp()) {
            case DELETE -> repo.deleteById(msg.getObjectId());
            default      -> index(msg);
        }
    }

    /* ───────── index / re‑index ───────── */

    private void index(PlanIndexMessage msg) throws IOException {
        JsonNode root = mapper.readTree(msg.getJson());
        String planId = msg.getObjectId();

        /* ---------- parent ---------- */
        PlanDocument parent = new PlanDocument();
        parent.setObjectId(planId);
        parent.setRelation(new JoinField<>("plan"));
        parent.setRouting(planId);
        parent.setPayload(asMap(root));
        repo.save(parent);

        /* ---------- linkedPlanServices children ---------- */
        for (JsonNode lpsNode : root.withArray("linkedPlanServices")) {
            String lpsId = lpsNode.get("objectId").asText();

            PlanDocument lpsChild = new PlanDocument();
            lpsChild.setObjectId(lpsId);
            lpsChild.setRelation(new JoinField<>("linkedPlanService", planId));
            lpsChild.setRouting(planId);
            lpsChild.setPayload(asMap(lpsNode));
            repo.save(lpsChild);
        }

        /* ---------- planCostShare child ---------- */
        JsonNode pcsNode = root.path("planCostShares");
        if (pcsNode.isObject()) {
            String pcsId = pcsNode.get("objectId").asText();   // or planId + "-pcs"
            PlanDocument pcsChild = new PlanDocument();
            pcsChild.setObjectId(pcsId);
            pcsChild.setRelation(new JoinField<>("planCostShare", planId));
            pcsChild.setRouting(planId);
            pcsChild.setPayload(asMap(pcsNode));
            repo.save(pcsChild);
        }
    }

    /* helper: JsonNode -> Map<String,Object> */
    private Map<String, Object> asMap(JsonNode node) {
        return mapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
    }
}