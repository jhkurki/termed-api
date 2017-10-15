package fi.thl.termed.service.node.internal;

import fi.thl.termed.domain.TypeId;
import fi.thl.termed.util.UUIDs;
import fi.thl.termed.util.dao.AbstractJdbcDao;
import fi.thl.termed.util.query.SqlSpecification;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.RowMapper;

public class JdbcNodeSequenceDao extends AbstractJdbcDao<TypeId, Integer> {

  public JdbcNodeSequenceDao(DataSource dataSource) {
    super(dataSource);
  }

  @Override
  public void insert(TypeId typeId, Integer value) {
    jdbcTemplate.update(
        "insert into node_sequence (graph_id, type_id, value) values (?, ?, ?)",
        typeId.getGraphId(),
        typeId.getId(),
        value);
  }

  @Override
  public void update(TypeId typeId, Integer value) {
    jdbcTemplate.update(
        "update node_sequence set value = ? where graph_id = ? and type_id = ?",
        value,
        typeId.getGraphId(),
        typeId.getId());
  }

  @Override
  public void delete(TypeId typeId) {
    jdbcTemplate.update(
        "delete from node_sequence where graph_id = ? and type_id = ?",
        typeId.getGraphId(),
        typeId.getId());
  }

  @Override
  protected <E> List<E> get(RowMapper<E> mapper) {
    return jdbcTemplate.query("select * from node_sequence", mapper);
  }

  @Override
  protected <E> List<E> get(SqlSpecification<TypeId, Integer> specification,
      RowMapper<E> mapper) {
    return jdbcTemplate.query(
        String.format("select * from node_sequence where %s", specification.sqlQueryTemplate()),
        specification.sqlQueryParameters(), mapper);
  }

  @Override
  public boolean exists(TypeId typeId) {
    return jdbcTemplate.queryForObject(
        "select count(*) from node_sequence where graph_id = ? and type_id = ?",
        Long.class,
        typeId.getGraphId(),
        typeId.getId()) > 0;
  }

  @Override
  protected <E> Optional<E> get(TypeId typeId, RowMapper<E> mapper) {
    return jdbcTemplate.query(
        "select * from node_sequence where graph_id = ? and type_id = ?",
        mapper,
        typeId.getGraphId(),
        typeId.getId()).stream().findFirst();
  }

  @Override
  protected RowMapper<TypeId> buildKeyMapper() {
    return (rs, rowNum) -> new TypeId(
        rs.getString("type_id"),
        UUIDs.fromString(rs.getString("graph_id")));
  }

  @Override
  protected RowMapper<Integer> buildValueMapper() {
    return (rs, rowNum) -> rs.getInt("value");
  }

}