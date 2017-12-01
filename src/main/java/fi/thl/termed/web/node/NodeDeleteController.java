package fi.thl.termed.web.node;

import static com.google.common.collect.Multimaps.filterValues;
import static fi.thl.termed.util.query.AndSpecification.and;
import static fi.thl.termed.util.service.WriteOptions.opts;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.springframework.http.HttpStatus.NO_CONTENT;

import fi.thl.termed.domain.Node;
import fi.thl.termed.domain.NodeId;
import fi.thl.termed.domain.User;
import fi.thl.termed.service.node.specification.NodesByGraphId;
import fi.thl.termed.service.node.specification.NodesByTypeId;
import fi.thl.termed.util.service.SaveMode;
import fi.thl.termed.util.service.Service;
import fi.thl.termed.util.spring.exception.NotFoundException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class NodeDeleteController {

  @Autowired
  private Service<NodeId, Node> nodeService;

  @DeleteMapping("/graphs/{graphId}/nodes")
  @ResponseStatus(NO_CONTENT)
  public void deleteAllOfGraph(
      @PathVariable("graphId") UUID graphId,
      @RequestParam(name = "sync", defaultValue = "false") boolean sync,
      @RequestParam(name = "disconnect", defaultValue = "false") boolean disconnect,
      @AuthenticationPrincipal User user) {
    List<NodeId> deleteIds = nodeService.getKeys(new NodesByGraphId(graphId), user);

    if (disconnect) {
      nodeService.saveAndDelete(collectRefsAndDisconnect(deleteIds, user),
          deleteIds, SaveMode.UPDATE, opts(sync), user);
    } else {
      nodeService.delete(deleteIds, opts(sync), user);
    }
  }

  @DeleteMapping(path = "/graphs/{graphId}/nodes", params = "batch=true")
  @ResponseStatus(NO_CONTENT)
  public void deleteByIdsOfGraph(
      @PathVariable("graphId") UUID graphId,
      @RequestParam(name = "sync", defaultValue = "false") boolean sync,
      @RequestParam(name = "disconnect", defaultValue = "false") boolean disconnect,
      @RequestBody List<NodeId> nodeIds,
      @AuthenticationPrincipal User user) {
    List<NodeId> deleteIds = nodeIds.stream()
        .map(id -> new NodeId(id.getId(), id.getTypeId(), graphId))
        .collect(toList());

    if (disconnect) {
      nodeService.saveAndDelete(collectRefsAndDisconnect(deleteIds, user),
          deleteIds, SaveMode.UPDATE, opts(sync), user);
    } else {
      nodeService.delete(deleteIds, opts(sync), user);
    }
  }

  @DeleteMapping("/graphs/{graphId}/types/{typeId}/nodes")
  @ResponseStatus(NO_CONTENT)
  public void deleteAllOfType(
      @PathVariable("graphId") UUID graphId,
      @PathVariable("typeId") String typeId,
      @RequestParam(name = "sync", defaultValue = "false") boolean sync,
      @RequestParam(name = "disconnect", defaultValue = "false") boolean disconnect,
      @AuthenticationPrincipal User user) {
    List<NodeId> deleteIds = nodeService.getKeys(and(
        new NodesByGraphId(graphId),
        new NodesByTypeId(typeId)), user);

    if (disconnect) {
      nodeService.saveAndDelete(collectRefsAndDisconnect(deleteIds, user),
          deleteIds, SaveMode.UPDATE, opts(sync), user);
    } else {
      nodeService.delete(deleteIds, opts(sync), user);
    }
  }

  @DeleteMapping(path = "/nodes", params = "batch=true")
  @ResponseStatus(NO_CONTENT)
  public void deleteByIds(
      @RequestParam(name = "sync", defaultValue = "false") boolean sync,
      @RequestParam(name = "disconnect", defaultValue = "false") boolean disconnect,
      @RequestBody List<NodeId> deleteIds,
      @AuthenticationPrincipal User user) {
    if (disconnect) {
      nodeService.saveAndDelete(collectRefsAndDisconnect(deleteIds, user),
          deleteIds, SaveMode.UPDATE, opts(sync), user);
    } else {
      nodeService.delete(deleteIds, opts(sync), user);
    }
  }

  @DeleteMapping(path = "/graphs/{graphId}/types/{typeId}/nodes", params = "batch=true")
  @ResponseStatus(NO_CONTENT)
  public void deleteByIdsOfType(
      @PathVariable("graphId") UUID graphId,
      @PathVariable("typeId") String typeId,
      @RequestParam(name = "sync", defaultValue = "false") boolean sync,
      @RequestParam(name = "disconnect", defaultValue = "false") boolean disconnect,
      @RequestBody List<NodeId> nodeIds,
      @AuthenticationPrincipal User user) {
    List<NodeId> deleteIds = nodeIds.stream()
        .map(id -> new NodeId(id.getId(), typeId, graphId))
        .collect(toList());

    if (disconnect) {
      nodeService.saveAndDelete(collectRefsAndDisconnect(deleteIds, user),
          deleteIds, SaveMode.UPDATE, opts(sync), user);
    } else {
      nodeService.delete(deleteIds, opts(sync), user);
    }
  }

  @DeleteMapping("/nodes")
  @ResponseStatus(NO_CONTENT)
  public void deleteById(
      @RequestParam(name = "sync", defaultValue = "false") boolean sync,
      @RequestParam(name = "disconnect", defaultValue = "false") boolean disconnect,
      @RequestBody NodeId deleteId,
      @AuthenticationPrincipal User user) {
    if (disconnect) {
      nodeService.saveAndDelete(collectRefsAndDisconnect(deleteId, user),
          singletonList(deleteId), SaveMode.UPDATE, opts(sync), user);
    } else {
      nodeService.delete(deleteId, opts(sync), user);
    }
  }

  @DeleteMapping("/graphs/{graphId}/types/{typeId}/nodes/{id}")
  @ResponseStatus(NO_CONTENT)
  public void deleteById(
      @PathVariable("graphId") UUID graphId,
      @PathVariable("typeId") String typeId,
      @PathVariable("id") UUID id,
      @RequestParam(name = "sync", defaultValue = "false") boolean sync,
      @RequestParam(name = "disconnect", defaultValue = "false") boolean disconnect,
      @AuthenticationPrincipal User user) {
    NodeId deleteId = new NodeId(id, typeId, graphId);
    if (disconnect) {
      nodeService.saveAndDelete(collectRefsAndDisconnect(deleteId, user),
          singletonList(deleteId), SaveMode.UPDATE, opts(sync), user);
    } else {
      nodeService.delete(deleteId, opts(sync), user);
    }
  }

  private List<Node> collectRefsAndDisconnect(List<NodeId> deleteIds, User user) {
    return deleteIds.stream()
        .flatMap(deleteId -> collectRefsAndDisconnect(deleteId, user).stream())
        .collect(toList());
  }

  private List<Node> collectRefsAndDisconnect(NodeId deleteId, User user) {
    Node delete = nodeService.get(deleteId, user).orElseThrow(NotFoundException::new);

    return delete.getReferrers().values().stream().map(referrerId -> {
      Node referrer = nodeService.get(referrerId, user).orElseThrow(IllegalStateException::new);
      referrer.setReferences(filterValues(referrer.getReferences(),
          referrerReferenceId -> !Objects.equals(referrerReferenceId, deleteId)));
      return referrer;
    }).collect(toList());
  }

}