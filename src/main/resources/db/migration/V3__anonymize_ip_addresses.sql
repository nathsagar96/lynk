-- Anonymize existing IP addresses and reduce column size for GDPR compliance
-- IPv4: 192.168.1.100 → 192.168.1.0
-- IPv6: 2001:0db8:85a3:0000:... → 2001:0db8:85a3::

-- Truncate IPv4 addresses (replace last octet with 0)
UPDATE click
SET ip_address = regexp_replace(ip_address, '(\d+\.\d+\.\d+)\.\d+', '\1.0')
WHERE ip_address ~ '^\d+\.\d+\.\d+\.\d+$';

-- Expand compressed IPv6 to full form (e.g., 2001:db8::dead:beef → 2001:0db8:0000:0000:0000:0000:dead:beef)
UPDATE click
SET ip_address = host(ip_address::inet)
WHERE ip_address ~ ':';

-- Truncate IPv6 addresses (keep first 3 groups + ::)
UPDATE click
SET ip_address = regexp_replace(ip_address, '^([0-9a-fA-F]{1,4}:[0-9a-fA-F]{1,4}:[0-9a-fA-F]{1,4}):.*$', '\1::')
WHERE ip_address ~ ':';

-- Reduce column length (20 chars max: "xxxx:xxxx:xxxx::" for IPv6, "xxx.xxx.xxx.0" for IPv4)
ALTER TABLE click ALTER COLUMN ip_address TYPE VARCHAR(20);
