package ca.jrvs.apps.trading.service;

import ca.jrvs.apps.trading.dao.MarketDataDao;
import ca.jrvs.apps.trading.dao.QuoteDao;
import ca.jrvs.apps.trading.model.config.MarketDataConfig;
import ca.jrvs.apps.trading.model.domain.IexQuote;
import ca.jrvs.apps.trading.model.domain.Quote;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.NoSuchElementException;
import javax.transaction.Transactional;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Transactional
@Service
public class QuoteService {

  private static final Logger logger = LoggerFactory.getLogger(QuoteService.class);
  private QuoteDao quoteDao;
  private MarketDataDao marketDataDao;

  @Autowired
  public QuoteService(QuoteDao quoteDao, MarketDataDao marketDataDao) {
    this.quoteDao = quoteDao;
    this.marketDataDao = marketDataDao;
  }

  /**
   * Update quote table against IEX source
   * - get all quotes from the db
   * - for each ticker get iexQuote
   * - convert iexQuote to quote entity
   * - persist quote to db
   *
   * @throws NoSuchElementException                      if ticker is not found from IEX
   * @throws org.springframework.dao.DataAccessException if unable to retrieve data
   * @throws IllegalArgumentException                    for invalid input
   */
  public void updateMarketData() {
    findAllQuotes().stream()
        .map(quote -> marketDataDao.findById(quote.getTicker())
            .orElseThrow(() -> new NoSuchElementException("ticker not found : " + quote.getTicker())
      )).map(QuoteService::buildQuoteFromIexQuote).forEach(quoteDao::save);
  }


  /**
   * Helper method. Map IexQuote to a Quote entity.
   * Note: `iexQuote.getLatestPrice() == null` if the stock market is closed.
   * Make sure set a default value for number field(s).
   */
  protected static Quote buildQuoteFromIexQuote(IexQuote iexQuote) {
    Quote quote = new Quote();

    quote.setId(Objects.requireNonNull(iexQuote.getSymbol(), "Id cannot be null"));

    if (iexQuote.getLatestPrice() == null) {
      quote.setLastPrice(0.0);
    } else {
      quote.setLastPrice(iexQuote.getLatestPrice());
    }

    if (iexQuote.getIexBidPrice() == null) {
      quote.setBidPrice(0.0);
    } else {
      quote.setBidPrice(iexQuote.getIexBidPrice());
    }

    if (iexQuote.getIexBidSize() == null) {
      quote.setAskSize(0L);
    } else {
      quote.setBidSize(iexQuote.getIexBidSize());
    }

    if (iexQuote.getIexAskSize() == null) {
      quote.setBidSize(0L);
    } else {
      quote.setAskSize(iexQuote.getIexAskSize());
    }

    if (iexQuote.getIexAskPrice() == null) {
      quote.setAskPrice(0.0);
    } else {
      quote.setAskPrice(iexQuote.getIexAskPrice());
    }

    return quote;
  }

  /**
   * Validate (against IEX) and save given tickers to quote table.
   *
   * - Get iexQuote(s)
   * - convert each iexQuote to Quote entity
   * - persist the quote to db
   *
   * @param tickers a list of tickers/symbols
   * @throws IllegalArgumentException if ticker is not found from IEX
   */
  public List<Quote> saveQuotes(List<String> tickers) {
    return tickers.stream()
        .map(this::saveQuote)
        .map(quote -> quoteDao.save(quote)).collect(Collectors.toList());
  }

  /**
   * Helper method
   */
  public Quote saveQuote(String ticker) {
    IexQuote iexQuote = marketDataDao.findById(ticker)
        .orElseThrow(() -> new IllegalArgumentException("ticker not found : " + ticker));
    return buildQuoteFromIexQuote(iexQuote);
  }

  /**
   * Find an IexQuote
   *
   * @param ticker id
   * @return IexQuote object
   * @throws IllegalArgumentException if ticker is invalid
   */
  public IexQuote findIexQuoteByTicker(String ticker) {
    return marketDataDao.findById(ticker)
        .orElseThrow(() -> new IllegalArgumentException(ticker + " is invalid"));
  }

  /**
   * Update a given quote to quote table without validation
   *
   * @param quote entity
   */
  public Quote saveQuote(Quote quote) {
    return quoteDao.save(quote);
  }

  /**
   * Find all quotes from the quote table
   *
   * @return a list of quotes
   */
  public List<Quote> findAllQuotes() {
    return quoteDao.findAll();
  }

}
