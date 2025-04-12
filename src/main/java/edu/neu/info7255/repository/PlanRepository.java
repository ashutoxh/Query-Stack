package edu.neu.info7255.repository;

import edu.neu.info7255.model.PlanDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface PlanRepository extends ElasticsearchRepository<PlanDocument, String> {
}