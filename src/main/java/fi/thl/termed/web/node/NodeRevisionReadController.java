package fi.thl.termed.web.node;

import static fi.thl.termed.util.collect.StreamUtils.toListAndClose;
import static fi.thl.termed.util.query.AndSpecification.and;
import static java.util.Comparator.comparing;

import fi.thl.termed.domain.Node;
import fi.thl.termed.domain.NodeId;
import fi.thl.termed.domain.ObjectRevision;
import fi.thl.termed.domain.Revision;
import fi.thl.termed.domain.RevisionId;
import fi.thl.termed.domain.RevisionType;
import fi.thl.termed.domain.User;
import fi.thl.termed.service.node.specification.NodeRevisionsByGraphId;
import fi.thl.termed.service.node.specification.NodeRevisionsById;
import fi.thl.termed.service.node.specification.NodeRevisionsByTypeId;
import fi.thl.termed.service.node.specification.NodeRevisionsLessOrEqualToRevisionNumber;
import fi.thl.termed.util.collect.Tuple2;
import fi.thl.termed.util.query.Queries;
import fi.thl.termed.util.service.Service;
import fi.thl.termed.util.spring.annotation.GetJsonMapping;
import fi.thl.termed.util.spring.exception.NotFoundException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class NodeRevisionReadController {

  @Autowired
  private Service<RevisionId<NodeId>, Tuple2<RevisionType, Node>> nodeRevisionService;

  @Autowired
  private Service<Long, Revision> revisionService;

  @GetJsonMapping("/graphs/{graphId}/types/{typeId}/nodes/{id}/revisions")
  public List<ObjectRevision<NodeId>> getNodeRevisions(
      @PathVariable("graphId") UUID graphId,
      @PathVariable("typeId") String typeId,
      @PathVariable("id") UUID id,
      @AuthenticationPrincipal User user) {
    return toListAndClose(nodeRevisionService
        .keys(Queries.query(and(
            NodeRevisionsById.of(id),
            NodeRevisionsByTypeId.of(typeId),
            NodeRevisionsByGraphId.of(graphId))), user)
        .map(revisionId -> {
          Revision revision = revisionService
              .get(revisionId.getRevision(), user)
              .orElseThrow(IllegalStateException::new);
          return new ObjectRevision<>(revision, null, revisionId.getId());
        }));
  }

  @GetJsonMapping("/graphs/{graphId}/types/{typeId}/nodes/{id}/revisions/{number}")
  public ObjectRevision<Node> getNodeRevision(
      @PathVariable("graphId") UUID graphId,
      @PathVariable("typeId") String typeId,
      @PathVariable("id") UUID id,
      @PathVariable("number") Long number,
      @AuthenticationPrincipal User user) {

    RevisionId<NodeId> revisionId = RevisionId.of(new NodeId(id, typeId, graphId), number);

    if (nodeRevisionService.exists(revisionId, user)) {
      Revision revision = revisionService.get(number, user)
          .orElseThrow(IllegalStateException::new);
      Tuple2<RevisionType, Node> nodeRevision = nodeRevisionService.get(revisionId, user)
          .orElseThrow(IllegalStateException::new);
      return new ObjectRevision<>(revision, nodeRevision._1, nodeRevision._2);
    }

    try (Stream<RevisionId<NodeId>> revisionIds = nodeRevisionService
        .keys(Queries.query(and(
            NodeRevisionsById.of(id),
            NodeRevisionsByTypeId.of(typeId),
            NodeRevisionsByGraphId.of(graphId),
            NodeRevisionsLessOrEqualToRevisionNumber.of(number))), user)) {

      RevisionId<NodeId> maxRevisionLessOrEqualToRequested = revisionIds
          .max(comparing(RevisionId::getRevision))
          .orElseThrow(NotFoundException::new);

      Revision revision = revisionService
          .get(maxRevisionLessOrEqualToRequested.getRevision(), user)
          .orElseThrow(IllegalStateException::new);
      Tuple2<RevisionType, Node> nodeRevision = nodeRevisionService
          .get(maxRevisionLessOrEqualToRequested, user)
          .orElseThrow(IllegalStateException::new);
      return new ObjectRevision<>(revision, nodeRevision._1, nodeRevision._2);
    }
  }

}
