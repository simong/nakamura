package org.sakaiproject.nakamura.google;


/**
 * 
 */
public class CrawlerException extends Exception {

  private static final long serialVersionUID = -472790654411242898L;

  public CrawlerException(String msg) {
    super(msg);
  }

  public CrawlerException(String msg, Exception e) {
    super(msg, e);
  }
  
}
