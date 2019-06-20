package resourcetojsonadapter;

import java.util.function.BiConsumer;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Component;

@Component (
    service = {AdapterFactory.class},
    property = {
    AdapterFactory.ADAPTABLE_CLASSES +"=org.apache.sling.api.resource.Resource",
    AdapterFactory.ADAPTER_CLASSES + "=javax.json.JsonObject"
    }
)

public class ResourceToJsonAdapter implements AdapterFactory
{
    @Override
    @SuppressWarnings("unchecked")
    public <AdapterType> AdapterType getAdapter (Object adaptable, Class<AdapterType> type) {
        Resource resource = (Resource) adaptable;
        if (resource != null) {
            ValueMap valuemap = resource.getValueMap();
            JsonObjectBuilder objectbuilder = Json.createObjectBuilder();
            
            BiConsumer<String, Object> biconsumer = (key, obj)-> objectbuilder.add(key, obj.toString());
            
            valuemap.forEach(biconsumer);
            
            JsonObject jsonobject = objectbuilder.build();
            
            return (AdapterType) jsonobject;
        }
        return null;
    }
}
