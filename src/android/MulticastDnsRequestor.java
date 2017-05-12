package com.koalasafe.cordova.plugin.multicastdns;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.concurrent.TimeoutException;

/**
 * Created by steve on 16/07/15.
 */
public class MulticastDnsRequestor {

    private static final String TAG = "MulticastDnsRequestor";
    private static final int BUFFER_SIZE = 4096;

    private final int queryTimeout;
    private final Boolean isIPv6;
    private final String logPrefix;
    private final String multicastIP;
    private final int port;
    private final NetworkInterface networkInterface;
    private final InetAddress multicastIPAddr;
    private final WifiManager wifiManager;
    private Context context;
    private MulticastSocket multicastSocket;

    public MulticastDnsRequestor(Context context) throws UnknownHostException, SocketException {
        this((WifiManager)context.getSystemService(Context.WIFI_SERVICE));
        this.context = context; //make sure we live long enough...
    }

    public MulticastDnsRequestor(WifiManager wifiManager) throws UnknownHostException, SocketException {
        //Common multicast ip and port for service discovery
        this("224.0.0.251", 5353, wifiManager);
    }

    public MulticastDnsRequestor(String multicastIP, int port, WifiManager wifiManager) throws UnknownHostException, SocketException {
        this(multicastIP, port, null, wifiManager);
    }

    public MulticastDnsRequestor(String multicastIP, int port, Context context) throws UnknownHostException, SocketException {
        this(multicastIP, port, null, (WifiManager)context.getSystemService(Context.WIFI_SERVICE));
        this.context = context; //make sure we live long enough...
    }

    public MulticastDnsRequestor(String multicastIP, int port, NetworkInterface networkInterface, WifiManager wifiManager) throws UnknownHostException, SocketException {
        this.multicastIP = multicastIP;
        this.multicastIPAddr = InetAddress.getByName(this.multicastIP);
        this.isIPv6 = this.multicastIPAddr instanceof Inet6Address;
        this.logPrefix = this.isIPv6 ? "ipv6: " : "ipv4: ";
        this.port = port;
        this.queryTimeout = 10000; //Milliseconds

        Log.d(TAG, String.format("Using %s:%s", multicastIPAddr, port));

        this.wifiManager = wifiManager;

        if (networkInterface == null) {
            this.networkInterface = getWifiNetworkInterface();
        } else {
            this.networkInterface = networkInterface;
        }
        if (this.networkInterface == null) {
            throw new SocketException("Could not locate wifi network interface.");
        }
        if (!this.networkInterface.supportsMulticast()) {
            Log.e(TAG, "networkInterface does not support multicast");
        }
    }

    /**
     * Send a multicast DNS request for the host, then listen until a response for the query is received
     *
     * @param host
     * @return
     * @throws IOException
     */
    public String query(String host) throws IOException, QueryTimeoutException {

        WifiManager.MulticastLock multicastLock = this.wifiManager.createMulticastLock("MulticastDNSRequestor");

        try {
            multicastLock.acquire();
            openSocket();

            DNSMessage q = new DNSMessage(host);
            byte[] queryBytes = q.serialize();

            DatagramPacket request = new DatagramPacket(queryBytes, queryBytes.length, this.multicastIPAddr, this.port);
            Log.i(TAG, this.logPrefix + "Sending Request: " + q);
            this.multicastSocket.send(request);
            Log.d(TAG, this.logPrefix + "Request Sent");

            byte[] responseBuffer = new byte[BUFFER_SIZE];
            DatagramPacket response = new DatagramPacket(responseBuffer, BUFFER_SIZE);

            long startTime = System.currentTimeMillis();
            while ((System.currentTimeMillis()-startTime) < this.queryTimeout) {
                openSocket();
                java.util.Arrays.fill(responseBuffer, (byte) 0); // clear buffer
                try {
                    Log.d(TAG, this.logPrefix + "About to receive");
                    multicastSocket.receive(response);
                    Log.d(TAG, this.logPrefix + "Received. Processing...");
                    try
                    {
                        DNSMessage responseMsg = new DNSMessage(response.getData(), response.getOffset(), response.getLength());
                        for (DNSAnswer a : responseMsg.getAnswers()) {
                            if (a.name.equals(host)) {
                                if (a.type == DNSComponent.Type.A) {
                                    Log.i(TAG, this.logPrefix + "Got IPv4 answer: " + a);
                                    return a.getRdataString().replace("/", "");
                                } else {
                                    Log.d(TAG, this.logPrefix + "Ignoring response type " + a.type);
                                }
                            } else{
                                Log.d(TAG, this.logPrefix + "Ignoring response for host " + a.name);
                            }
                        }
                   
                        Log.d(TAG, this.logPrefix + "Ignored response " + responseMsg); 
                        
                    } catch (Exception dnsEx) {
                        Log.v(TAG, this.logPrefix + "Unsupported packet. Message: " + dnsEx.getMessage());
                    }
                }
                catch (SocketTimeoutException stEx) {
                    Log.v(TAG, this.logPrefix + "Timeout");
                }
                catch (IOException ioEx) {
                    Log.w(TAG, this.logPrefix + "Error during query: " + ioEx.getStackTrace());
                }
            }        

        } finally {
            multicastLock.release();
        }

        throw new QueryTimeoutException();
    }

    private void openSocket() throws IOException {
        this.multicastSocket = new MulticastSocket(this.port);
        multicastSocket.setTimeToLive(2);
        multicastSocket.setReuseAddress(true);
        multicastSocket.setNetworkInterface(networkInterface);
        multicastSocket.joinGroup(this.multicastIPAddr);
        multicastSocket.setSoTimeout(5000);
    }

    public NetworkInterface getWifiNetworkInterface() throws SocketException {
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();

        for(Enumeration<NetworkInterface> list = NetworkInterface.getNetworkInterfaces(); list.hasMoreElements();)
        {
            NetworkInterface i = list.nextElement();
            for (InterfaceAddress ifAddr : i.getInterfaceAddresses()) {
                int wifiIp = wifiInfo.getIpAddress();
                int ifIp = inetAddressToInt(ifAddr.getAddress());

                Log.d(TAG, this.logPrefix + String.format("Comparing %s and %s", wifiIp, ifIp));

                if (wifiIp==ifIp) {
                    Log.i(TAG, this.logPrefix + String.format("Using %s as the network interface as it matches WifiManager IP of %s", i, wifiInfo.getIpAddress()));
                    return i;
                }
            }
        }
        Log.e(TAG, this.logPrefix + "Couldn't find a network interface for the wifi IP: " + wifiInfo.getIpAddress());
        return null;
    }

    /**
     * From android.net.NetworkUtils which is not available
     * @param inetAddr
     * @return
     * @throws IllegalArgumentException
     */
    private static int inetAddressToInt(InetAddress inetAddr)
            throws IllegalArgumentException {

        byte [] addr = inetAddr.getAddress();
        return  ((addr[3] & 0xff) << 24) |
                ((addr[2] & 0xff) << 16) |
                ((addr[1] & 0xff) << 8) |
                 (addr[0] & 0xff);
    }

}
