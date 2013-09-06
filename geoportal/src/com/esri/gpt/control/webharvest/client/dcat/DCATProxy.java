/*
 * Copyright 2013 Esri.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.esri.gpt.control.webharvest.client.dcat;

import com.esri.gpt.framework.http.HttpClientRequest;
import com.esri.gpt.framework.http.ResponseInfo;
import com.esri.gpt.framework.http.StringHandler;
import com.esri.gpt.framework.resource.query.Criteria;
import java.io.IOException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.util.DateParseException;
import org.apache.commons.httpclient.util.DateUtil;

/**
 * DCAT proxy.
 */
class DCATProxy {

  /**
   * logger
   */
  private static final Logger LOGGER = Logger.getLogger(DCATProxy.class.getCanonicalName());
  /**
   * service info
   */
  private DCATInfo info;
  /**
   * criteria
   */
  private Criteria criteria;

  /**
   * Creates instance of the proxy.
   *
   * @param info service info
   * @param criteria criteria
   */
  public DCATProxy(DCATInfo info, Criteria criteria) {
    if (info == null) {
      throw new IllegalArgumentException("No info provided.");
    }
    this.info = info;
    this.criteria = criteria;
  }

  /**
   * Reads content.
   * @param url content URL
   * @return content
   * @throws IOException if reading content fails
   */
  public Content readContent(String url) throws IOException {
    LOGGER.log(Level.FINER, "Reading metadata of source URI: \"{0}\" through proxy: {1}", new Object[]{url, this});
    HttpClientRequest cr = new HttpClientRequest();
    cr.setBatchHttpClient(this.info.getBatchHttpClient());
    cr.setUrl(url);
    BreakableStringHandler sh = new BreakableStringHandler(criteria != null ? criteria.getFromDate() : null);
    cr.setContentHandler(sh);
    cr.execute();
    String mdText = sh.getContent();
    LOGGER.log(Level.FINER, "Received metadata of source URI: \"{0}\" through proxy: {1}", new Object[]{url, this});
    LOGGER.finest(mdText);
    return new Content(sh.getLastModifiedDate(), mdText);
  }

  /**
   * Finds last modified date.
   *
   * @param headers array of headers
   * @return last modified date or <code>null</code> if date not found
   */
  private static Date findLastModifiedDate(ResponseInfo responseInfo) {
    Header lastModfiedHeader = responseInfo.getResponseHeader("Last-Modified");
    if (lastModfiedHeader != null) {
      try {
        return DateUtil.parseDate(lastModfiedHeader.getValue());
      } catch (DateParseException ex) {
        return null;
      }
    }
    return null;
  }

  /**
   * Content
   */
  public static class Content {

    private Date lastModifiedDate;
    private String text;

    /**
     * Creates instance of the content
     *
     * @param lastModifiedDate last modified date
     * @param text text read from stream
     */
    public Content(Date lastModifiedDate, String text) {
      this.lastModifiedDate = lastModifiedDate;
      this.text = text;
    }

    /**
     * Gets last modified date
     *
     * @return last modified date or <code>null</code> if last modified date not
     * available
     */
    public Date getLastModifedDate() {
      return lastModifiedDate;
    }

    /**
     * Gets text.
     *
     * @return text
     */
    public String getText() {
      return text;
    }
  }

  /**
   * Breakable string handler.
   */
  private static class BreakableStringHandler extends StringHandler {

    private Date fromDate;
    private Date lastModifiedDate;

    /**
     * Creates instance of the handler.
     * @param fromDate date content older than will be considered
     */
    public BreakableStringHandler(Date fromDate) {
      this.fromDate = fromDate;
    }

    /**
     * Gets last modified date.
     * @return last modified date
     */
    public Date getLastModifiedDate() {
      return lastModifiedDate;
    }

    @Override
    public boolean onBeforeReadResponse(HttpClientRequest request) {
      lastModifiedDate = findLastModifiedDate(request.getResponseInfo());
      if (fromDate != null && lastModifiedDate != null && lastModifiedDate.before(fromDate)) {
        return false;
      } else {
        return true;
      }
    }
  }
}
