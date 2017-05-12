package com.koalasafe.cordova.plugin.multicastdns;
public class QueryTimeoutException extends Exception
{
    private static  long serialVersionUID = 1997753363232807019L;
    public QueryTimeoutException()
    {
        super("Timeout occurred before response was recieved");
    }


}