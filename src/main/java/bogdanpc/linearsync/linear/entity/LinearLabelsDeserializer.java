package bogdanpc.linearsync.linear.entity;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

public class LinearLabelsDeserializer extends JsonDeserializer<LinearIssue.LinearLabels> {

    @Override
    public LinearIssue.LinearLabels deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();

        if (p.getCurrentToken() == JsonToken.START_ARRAY) {
            // Direct array of labels
            List<LinearIssue.LinearLabel> labels = mapper.readValue(p, mapper.getTypeFactory().constructCollectionType(List.class, LinearIssue.LinearLabel.class));
            return new LinearIssue.LinearLabels(labels);
        } else if (p.getCurrentToken() == JsonToken.START_OBJECT) {
            // Object with nodes property
            JsonNode labelsNode = mapper.readTree(p);
            if (labelsNode.has("nodes")) {
                List<LinearIssue.LinearLabel> labels = mapper.convertValue(
                    labelsNode.get("nodes"),
                    mapper.getTypeFactory().constructCollectionType(List.class, LinearIssue.LinearLabel.class));
                return new LinearIssue.LinearLabels(labels);
            } else {
                return LinearIssue.LinearLabels.empty();
            }
        } else {
            return LinearIssue.LinearLabels.empty();
        }
    }
}