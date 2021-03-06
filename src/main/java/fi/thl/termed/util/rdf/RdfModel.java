package fi.thl.termed.util.rdf;

import java.util.List;
import java.util.Optional;

public interface RdfModel {

  /**
   * List subjects that match provided predicate and object. To get for example all foaf:Person
   * instances in the model, one could use find("http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
   * "http://xmlns.com/foaf/0.1/Person");
   */
  List<RdfResource> find(String predicateUri, String objectUri);

  /**
   * Find resource by uri
   */
  Optional<RdfResource> find(String subjectUri);

  /**
   * List all subjects.
   */
  List<RdfResource> find();

  /**
   * Save resources with literal and object properties (i.e. all triples describing a resource).
   */
  RdfModel save(List<RdfResource> resources);

}
