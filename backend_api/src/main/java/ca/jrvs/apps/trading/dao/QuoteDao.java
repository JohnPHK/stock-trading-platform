package ca.jrvs.apps.trading.dao;

import ca.jrvs.apps.trading.model.domain.Quote;

import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

@Repository
public class QuoteDao implements CrudRepository<Quote, String> {

  private static final String TABLE_NAME = "quote";
  private static final String ID_COLUMN_NAME = "ticker";

  private static final Logger logger = LoggerFactory.getLogger(QuoteDao.class);
  private final JdbcTemplate jdbcTemplate;
  private final SimpleJdbcInsert simpleJdbcInsert;

  @Autowired
  public QuoteDao(DataSource dataSource) {
    jdbcTemplate = new JdbcTemplate(dataSource);
    simpleJdbcInsert = new SimpleJdbcInsert(dataSource).withTableName(TABLE_NAME);
  }

  /**
   * hint: http://bit.ly/2sDz8hq DataAccessException family
   *
   * @throws DataAccessException for unexpected SQL result or SQL execution failure
   */
  @Override
  public Quote save(Quote quote) {
    if (existsById(quote.getTicker())) {
      int updatedRowNo = updateOne(quote);
      if (updatedRowNo != 1) {
        throw new DataRetrievalFailureException("Unable to update quote");
      }
    } else {
      addOne(quote);
    }
    return quote;
  }

  /**
   * helper method that saves one quote
   */
  private void addOne(Quote quote) {
    SqlParameterSource parameterSource = new BeanPropertySqlParameterSource(quote);
    int row = simpleJdbcInsert.execute(parameterSource);
    if (row != 1) {
      throw new IncorrectResultSizeDataAccessException("Failed to insert", 1, row);
    }
  }

  /**
   * helper method that updates one quote
   */
  private int updateOne(Quote quote) {
    String update_sql = "UPDATE " + TABLE_NAME + " SET last_price=?, bid_price=?,"
        + "bid_size=?, ask_price=?, ask_size=? WHERE " + ID_COLUMN_NAME + " = ?";
    return jdbcTemplate.update(update_sql, makeUpdateValues(quote));
  }

  /**
   * helper method that makes sql update values objects
   *
   * @param quote to be updated
   * @return UPDATE_SQL values
   */
  private Object[] makeUpdateValues(Quote quote) {
    return new Object[]{quote.getLastPrice(), quote.getBidPrice(), quote.getBidSize(),
        quote.getAskPrice(), quote.getAskSize(), quote.getTicker()};
  }

  /**
   * helper method that makes sql add values objects
   *
   * @param quote to be added
   * @return ADD_SQL values
   */
  private Object[] makeAddValues(Quote quote) {
    return new Object[]{quote.getTicker(), quote.getLastPrice(), quote.getBidPrice(),
        quote.getBidSize(), quote.getAskPrice(), quote.getAskSize()};
  }

  @Override
  public <S extends Quote> List<S> saveAll(Iterable<S> quotes) {
    String updateSql = "UPDATE " + TABLE_NAME
        + " SET last_price=?, bid_price=?, bid_size=?, ask_price=?, ask_size=? WHERE "
        + ID_COLUMN_NAME + " =?";
    String insertSql = "INSERT INTO " + TABLE_NAME + " VALUES (?, ?, ?, ?, ?, ?)";

    List<Object[]> batchUpdate = new ArrayList<>();
    List<Object[]> batchAdd = new ArrayList<>();

    quotes.forEach(quote -> {
      if (quote.getTicker() == null) {
        throw new IllegalArgumentException("null quote available");
      }
      if (!existsById(quote.getTicker())) {
        batchAdd.add(makeAddValues(quote));
      } else {
        batchUpdate.add(makeUpdateValues(quote));
      }
    });


    int[] updateRows = jdbcTemplate.batchUpdate(updateSql, batchUpdate);
    int[] insertRows = jdbcTemplate.batchUpdate(insertSql, batchAdd);
    int totalRow = Arrays.stream(updateRows).sum() + Arrays.stream(insertRows).sum();
    if (totalRow != Iterables.size(quotes)) {
      throw new IncorrectResultSizeDataAccessException("Number of rows ", Iterables.size(quotes),
          totalRow);
    }

    return StreamSupport.stream(quotes.spliterator(), false)
        .collect(Collectors.toList());
  }

  /**
   * Find a quote by ticker
   *
   * @param ticker name
   * @return quote or Optional.empty if not found
   */
  @Override
  public Optional<Quote> findById(String ticker) {
    String selectSql = "SELECT * FROM " + TABLE_NAME + " WHERE " + ID_COLUMN_NAME + " = ?";
    Quote resQuote;
    try {
      resQuote = jdbcTemplate.queryForObject(selectSql,
          BeanPropertyRowMapper.newInstance(Quote.class), ticker);
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
    return Optional.ofNullable(resQuote);
  }

  /**
   * return all quotes
   *
   * @throws DataAccessException if failed to update
   */
  @Override
  public List<Quote> findAll() {
    String selectAllSql = "SELECT * FROM " + TABLE_NAME;
    return jdbcTemplate.query(selectAllSql, new BeanPropertyRowMapper<>(Quote.class));
  }

  @Override
  public boolean existsById(String ticker) {
    return findById(ticker).isPresent();
  }

  @Override
  public long count() {
    String countSql = "SELECT COUNT(*) FROM " + TABLE_NAME;

    Long res = jdbcTemplate.queryForObject(countSql, Long.class);
    if (res == null) {
      throw new IllegalStateException("Unable to extract required info from db");
    }
    return res;
  }

  @Override
  public void deleteById(String ticker) {
    if (!(existsById(ticker))) {
      throw new IllegalArgumentException("ID not available");
    }
    String deleteSql = "DELETE FROM " + TABLE_NAME + " WHERE " + ID_COLUMN_NAME + " =?";
    jdbcTemplate.update(deleteSql, ticker);
  }

  @Override
  public void deleteAll() {
    String deleteSql = "DELETE FROM " + TABLE_NAME;
    jdbcTemplate.update(deleteSql);
  }

  @Override
  public Iterable<Quote> findAllById(Iterable<String> strings) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void delete(Quote quote) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void deleteAll(Iterable<? extends Quote> entities) {
    throw new UnsupportedOperationException("Not implemented");
  }


}
