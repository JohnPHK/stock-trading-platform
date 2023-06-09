package ca.jrvs.apps.trading.dao;

import ca.jrvs.apps.trading.model.domain.Account;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class AccountDao extends JdbcCrudDao<Account> {

  private static final Logger logger = LoggerFactory.getLogger(AccountDao.class);

  private final String TABLE_NAME = "account";
  private final String ID_COLUMN = "id";

  private JdbcTemplate jdbcTemplate;
  private SimpleJdbcInsert simpleInsert;

  @Autowired
  public AccountDao(DataSource dataSource) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
    this.simpleInsert = new SimpleJdbcInsert(dataSource).withTableName(TABLE_NAME)
        .usingGeneratedKeyColumns(ID_COLUMN);
  }

  @Override
  public JdbcTemplate getJdbcTemplate() {
    return jdbcTemplate;
  }

  @Override
  public SimpleJdbcInsert getSimpleJdbcInsert() {
    return simpleInsert;
  }

  @Override
  public String getTableName() {
    return TABLE_NAME;
  }

  @Override
  public String getIdColumnName() {
    return ID_COLUMN;
  }

  @Override
  Class<Account> getEntityClass() {
    return Account.class;
  }

  @Override
  public int updateOne(Account account) {
    String updateSql =
        "UPDATE " + getTableName() + " SET trader_id=?, amount=? WHERE " + getIdColumnName()
            + " =?";
    return jdbcTemplate.update(updateSql, makeUpdateValues(account));
  }

  public Account updateAmountById(Integer id, Double newAmount) {
    String updateSql = "UPDATE " + getTableName() + " SET amount=? WHERE " + getIdColumnName()
        + " =?";
    jdbcTemplate.update(updateSql, newAmount, id);
    Optional<Account> optionalUpdatedAccount = findByTraderId(id);
    if (!optionalUpdatedAccount.isPresent()) {
      throw new DataRetrievalFailureException("updated account is could not be retrieved");
    }
    Account updatedAccount =optionalUpdatedAccount.get();
    System.out.println(updatedAccount.getAmount());
    System.out.println(newAmount);
    if (updatedAccount.getAmount() != newAmount) {
      throw new IllegalStateException("amount failed to be updated");
    }
    return updatedAccount;
  }

  private Object[] makeUpdateValues(Account account) {
    return new Object[]{account.getTraderId(), account.getAmount(), account.getId()};
  }

  public void deleteByTraderId(Integer traderId) {
    if (!(existsByTraderId(traderId))){
      throw new IllegalArgumentException("The trader_id not available");
    }
    String deleteSql = "DELETE FROM " + getTableName() + " WHERE trader_id=?";
    getJdbcTemplate().update(deleteSql, traderId);
  }

  public Optional<Account> findByTraderId(Integer traderId) {
    Optional<Account> account = Optional.empty();
    String selectSql = "SELECT * FROM " + getTableName() + " WHERE trader_id=?";
    try {
      account = Optional.ofNullable(getJdbcTemplate().queryForObject(selectSql,
          BeanPropertyRowMapper.newInstance(getEntityClass()), traderId));
    } catch (IncorrectResultSizeDataAccessException e) {
      logger.debug("Can't find trader_id: " + traderId, e);
    }

    return account;
  }

  public boolean existsByTraderId(Integer traderId) {
    return findByTraderId(traderId).isPresent();
  }

  @Override
  public <S extends Account> Iterable<S> saveAll(Iterable<S> iterable) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void delete(Account account) {
    throw new UnsupportedOperationException("Not implemented");
  }
}
