/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ORG.oclc.os.SRW;

import gov.loc.www.zing.srw.ExtraDataType;
import gov.loc.www.zing.srw.SearchRetrieveRequestType;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.axis.transport.http.HTTPTransport;
import org.apache.axis.types.NonNegativeInteger;
import org.apache.axis.types.PositiveInteger;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLTermNode;

/**
 *
 * @author Ralph
 */
class OpenSearchQueryResult extends QueryResult {
    private static final Log log=LogFactory.getLog(OpenSearchQueryResult.class);
    String query;
    int count=0;
    
    public OpenSearchQueryResult(String queryStr, SearchRetrieveRequestType request,
            SRWOpenSearchDatabase db) throws InstantiationException, SRWDiagnostic {
        this.query=query;
        // figure out what schema/template to use
        String schema=request.getRecordSchema();
        if(schema==null)
            schema=db.defaultSchemaID;
        if(schema==null)
            schema=db.defaultSchemaName;
        if(schema==null) {
            log.error("No schema provided");
            throw new InstantiationException("No schema provided");
        }

        CQLParser parser = new CQLParser(CQLParser.V1POINT1);
        CQLNode query;
        try {
            query=parser.parse(queryStr);
        }
        catch(CQLParseException e) {
            throw new SRWDiagnostic(SRWDiagnostic.QuerySyntaxError, queryStr);
        } catch (IOException e) {
            throw new SRWDiagnostic(SRWDiagnostic.QuerySyntaxError, queryStr);
        }
        if(!(query instanceof CQLTermNode)) {
            throw new SRWDiagnostic(SRWDiagnostic.UnsupportedBooleanOperator, null);
        }
        CQLTermNode term=(CQLTermNode) query;
        
        String template=db.templates.get(schema);
        log.debug("template="+template);
        if(template==null)
            throw new SRWDiagnostic(SRWDiagnostic.RecordNotAvailableInThisSchema, schema);
        Pattern p=Pattern.compile("\\{([^\\}]+)\\}");
        Matcher m=p.matcher(template);
        String parameter;
        while(m.find()) {
            parameter=m.group();
            log.debug("template parameter="+parameter);
            if(parameter.equals("{searchTerms}"))
                template=template.replace(parameter, term.getTerm());
            else if(parameter.equals("{count}")) {
                NonNegativeInteger nni = request.getMaximumRecords();
                if(nni!=null)
                    count=nni.intValue();
                else {
                    count=db.defaultNumRecs;
                }
                if(count<=0)
                    throw new InstantiationException("maximumRecords parameter not supplied and defaultNumRecs not specified in the database properties file");
                template=template.replace(parameter, Integer.toString(count));
            }
            else if(parameter.equals("{startIndex}")) {
                PositiveInteger pi = request.getStartRecord();
                int start;
                if(pi!=null)
                    start=pi.intValue();
                else {
                    start=1;
                }
                template=template.replace(parameter, Integer.toString(start));
            }
            else if(parameter.equals("{startPage}")) {
                PositiveInteger pi = request.getStartRecord();
                int start;
                if(pi!=null) {
                    start=pi.intValue();
                    if(db.itemsPerPage==0)
                        throw new InstantiationException("template expects startPage parameter but itemsPerPage not specified in the database properties file");
                    start=start/db.itemsPerPage;
                }
                else {
                    start=1;
                }
                template=template.replace(parameter, Integer.toString(start));
            }
        }
        int i=template.indexOf('?');
        if(i>0)
            try {
                template=template.substring(0, i+1)+URLEncoder.encode(template.substring(i+1), "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(OpenSearchQueryResult.class.getName()).log(Level.SEVERE, null, ex);
        }
        log.debug("url="+template);
        URL url;
        try {
            url=new URL(template);
        } catch (MalformedURLException ex) {
            throw new SRWDiagnostic(SRWDiagnostic.GeneralSystemError, template);
        }
        try {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.connect();
            log.debug("contentType="+conn.getContentType());
            log.debug("responseCode="+conn.getResponseCode());
        } catch (IOException ex) {
            throw new SRWDiagnostic(SRWDiagnostic.GeneralSystemError, ex.getMessage());
        }
    }

    @Override
    public long getNumberOfRecords() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public RecordIterator newRecordIterator(long index, int numRecs, String schemaId, ExtraDataType edt) throws InstantiationException {
        return new OpenSearchRecordIterator(index, numRecs, schemaId, edt);
    }
    
}
