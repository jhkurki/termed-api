package fi.thl.termed.service.node.internal;

import static java.util.Optional.ofNullable;

import com.google.common.base.Strings;
import fi.thl.termed.domain.Node;
import fi.thl.termed.domain.NodeId;
import fi.thl.termed.domain.RevisionId;
import fi.thl.termed.domain.RevisionType;
import fi.thl.termed.util.UUIDs;
import fi.thl.termed.util.collect.Tuple;
import fi.thl.termed.util.collect.Tuple2;
import fi.thl.termed.util.dao.AbstractJdbcDao;
import fi.thl.termed.util.query.SqlSpecification;
import java.util.Optional;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.springframework.jdbc.core.RowMapper;

public class JdbcNodeRevisionDao extends
    AbstractJdbcDao<RevisionId<NodeId>, Tuple2<RevisionType, Node>> {

  public JdbcNodeRevisionDao(DataSource dataSource) {
    super(dataSource);
  }

  @Override
  public void insert(RevisionId<NodeId> revisionId, Tuple2<RevisionType, Node> revision) {
    NodeId nodeId = revisionId.getId();
    Optional<Node> node = ofNullable(revision._2);
    jdbcTemplate.update(
        "insert into node_aud (graph_id, type_id, id, revision, code, uri, number, created_by, created_date, last_modified_by, last_modified_date, revision_type) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        nodeId.getTypeGraphId(),
        nodeId.getTypeId(),
        nodeId.getId(),
        revisionId.getRevision(),
        node.flatMap(Node::getCode).map(Strings::emptyToNull).orElse(null),
        node.flatMap(Node::getUri).map(Strings::emptyToNull).orElse(null),
        node.map(Node::getNumber).orElse(null),
        node.map(Node::getCreatedBy).orElse(null),
        node.map(Node::getCreatedDate).orElse(null),
        node.map(Node::getLastModifiedBy).orElse(null),
        node.map(Node::getLastModifiedDate).orElse(null),
        revision._1.toString());
  }

  @Override
  public void update(RevisionId<NodeId> revisionId, Tuple2<RevisionType, Node> revision) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void delete(RevisionId<NodeId> revisionId) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected <E> Stream<E> get(
      SqlSpecification<RevisionId<NodeId>, Tuple2<RevisionType, Node>> specification,
      RowMapper<E> mapper) {
    return jdbcTemplate.queryForStream(
        String.format("select * from node_aud where %s order by revision desc",
            specification.sqlQueryTemplate()),
        specification.sqlQueryParameters(), mapper);
  }

  @Override
  public boolean exists(RevisionId<NodeId> revisionIdId) {
    NodeId nodeId = revisionIdId.getId();
    return jdbcTemplate.queryForOptional(
        "select count(*) from node_aud where graph_id = ? and type_id = ? and id = ? and revision = ?",
        Long.class,
        nodeId.getTypeGraphId(),
        nodeId.getTypeId(),
        nodeId.getId(),
        revisionIdId.getRevision())
        .orElseThrow(IllegalStateException::new) > 0;
  }

  @Override
  protected <E> Optional<E> get(RevisionId<NodeId> revisionIdId, RowMapper<E> mapper) {
    NodeId nodeId = revisionIdId.getId();
    return jdbcTemplate.queryForFirst(
        "select * from node_aud where graph_id = ? and type_id = ? and id = ? and revision = ?",
        mapper,
        nodeId.getTypeGraphId(),
        nodeId.getTypeId(),
        nodeId.getId(),
        revisionIdId.getRevision());
  }

  @Override
  protected RowMapper<RevisionId<NodeId>> buildKeyMapper() {
    return (rs, rowNum) -> RevisionId.of(
        new NodeId(UUIDs.fromString(rs.getString("id")),
            rs.getString("type_id"),
            UUIDs.fromString(rs.getString("graph_id"))),
        rs.getLong("revision"));
  }

  @Override
  protected RowMapper<Tuple2<RevisionType, Node>> buildValueMapper() {
    return (rs, rowNum) -> {
      Node.Builder builder = Node.builder()
          .id(UUIDs.fromString(rs.getString("id")),
              rs.getString("type_id"),
              UUIDs.fromString(rs.getString("graph_id")));

      builder.code(rs.getString("code"));
      builder.uri(rs.getString("uri"));

      long number = rs.getLong("number");
      builder.number(rs.wasNull() ? null : number);

      builder.createdBy(rs.getString("created_by"));
      builder.lastModifiedBy(rs.getString("last_modified_by"));
      builder.createdDate(rs.getTimestamp("created_date") != null
          ? rs.getTimestamp("created_date").toLocalDateTime() : null);
      builder.lastModifiedDate(rs.getTimestamp("last_modified_date") != null
          ? rs.getTimestamp("last_modified_date").toLocalDateTime() : null);

      return Tuple.of(RevisionType.valueOf(rs.getString("revision_type")), builder.build());
    };
  }

}
