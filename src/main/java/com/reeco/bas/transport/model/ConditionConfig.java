package com.reeco.bas.transport.model;

import lombok.ToString;
import org.codehaus.jackson.annotate.JsonProperty;

@ToString
public class ConditionConfig {

    @JsonProperty("status_id")
    private int statusId;

    @JsonProperty("operator")
    private String operator;

    @JsonProperty("value")
    private Double value;

    public int getStatusId() {
        return statusId;
    }

    public void setStatusId(int statusId) {
        this.statusId = statusId;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }
}
