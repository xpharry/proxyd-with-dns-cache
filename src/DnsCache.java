import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class DnsCache {
    private final String host;
    private final String[] ips;
    private final Date expiration;

    public String getHost() {
        return host;
    }

    public String[] getIps() {
        String[] copy = new String[ips.length];
        System.arraycopy(ips, 0, copy, 0, ips.length); // defensive copy
        return copy;
    }

    public String getIp() {
        return ips[0];
    }

    public Date getExpiration() {
        return expiration;
    }

    public DnsCache(String host, String[] ips, Date expiration) {
        this.host = host;
        this.ips = ips;
        this.expiration = expiration;
    }

    @Override
    public String toString() {
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

        return "DnsCacheEntry{" +
                "host='" + host + '\'' +
                ", ips=" + Arrays.toString(ips) +
                ", expiration=" + dateFormat.format(expiration) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DnsCache that = (DnsCache) o;

        if (host != null ? !host.equals(that.host) : that.host != null)
            return false;
        if (!Arrays.equals(ips, that.ips)) return false;
        return !(expiration != null ? !expiration.equals(that.expiration) : that.expiration != null);
    }

}
