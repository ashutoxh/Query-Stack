package edu.neu.info7255.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlanIndexMessage {
    private String objectId;
    private String json;          // full plan json
    private Operation op;         // CREATE, PATCH, DELETE

    public enum Operation { CREATE, PATCH, DELETE }
}
