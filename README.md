# Simple Email Server

A lightweight Java-based email server implementation supporting SMTP, POP3, and IMAP protocols.

## Features

### Protocol Support
- **SMTP** (Simple Mail Transfer Protocol) - Port 2525
  - AUTH LOGIN and PLAIN authentication
  - Email sending and receiving
  - Multi-recipient support
- **POP3** (Post Office Protocol v3) - Port 11110
  - Email retrieval
  - Message deletion
- **IMAP** (Internet Message Access Protocol) - Port 11143
  - Mailbox management
  - Folder support (INBOX, Sent, Drafts, Trash)
  - Message flags and search

### Core Features
- ✅ **Email-based mailbox directories** (`user@domain/`)
- ✅ **Multi-domain support** with local domain configuration
- ✅ **File-based user repository** with email authentication
- ✅ **Session management** for concurrent connections
- ✅ **Asynchronous networking** using Netty framework
- ⏳ **Index caching** (planned optimization)
- ⏳ **MX mode** for unauthenticated local delivery (planned)

## Quick Start

### Prerequisites
- Java 11 or higher
- Maven 3.6+

### Build
```bash
mvn clean package
```

### Run
```bash
mvn exec:java -Dexec.mainClass="com.email.server.SmtpServerApplication"
```

### Test Accounts
```
alice@localhost / password123
bob@localhost / password456
charlie@localhost / password789
```

## Configuration

Configuration is managed in `src/main/resources/application.conf`:

```hocon
smtp {
  host = "0.0.0.0"
  port = 2525
}

pop3 {
  host = "0.0.0.0"
  port = 11110
}

imap {
  host = "0.0.0.0"
  port = 11143
}

domains.local = ["localhost", "example.com", "mail.local"]

users = [
  { email = "alice@localhost", password = "password123" }
  { email = "bob@localhost", password = "password456" }
  { email = "charlie@localhost", password = "789" }
]
```

## Testing

### Send Email via SMTP
```bash
{
  echo "EHLO test"
  sleep 0.3
  echo "AUTH LOGIN"
  sleep 0.3
  echo "YWxpY2VAbG9jYWxob3N0"  # alice@localhost
  sleep 0.3
  echo "cGFzc3dvcmQxMjM="       # password123
  sleep 0.3
  echo "MAIL FROM:<alice@localhost>"
  sleep 0.3
  echo "RCPT TO:<bob@localhost>"
  sleep 0.3
  echo "DATA"
  sleep 0.2
  echo "From: alice@localhost"
  echo "To: bob@localhost"
  echo "Subject: Test Email"
  echo ""
  echo "Hello from alice!"
  echo "."
  sleep 0.2
  echo "QUIT"
} | nc localhost 2525
```

### Retrieve Emails via POP3
```bash
{
  echo "USER bob@localhost"
  sleep 0.2
  echo "PASS password456"
  sleep 0.2
  echo "LIST"
  sleep 0.2
  echo "RETR 1"
  sleep 0.2
  echo "QUIT"
} | nc localhost 11110
```

### Access via IMAP
```bash
{
  echo "001 LOGIN bob@localhost password456"
  sleep 0.3
  echo "002 SELECT INBOX"
  sleep 0.3
  echo "003 FETCH 1 (BODY[])"
  sleep 0.2
  echo "004 LOGOUT"
} | nc localhost 11143
```

## Architecture

### Key Components

- **SmtpServer** / **Pop3Server** / **ImapServer**: Protocol-specific servers
- **MailboxStorage**: File-based email storage with folder support
- **UserRepository**: User authentication and management
- **SessionManager**: Connection and session lifecycle management
- **Handlers**: Protocol command handlers using Netty

### Directory Structure
```
./data/mailboxes/
├── alice@localhost/
│   ├── .meta
│   ├── INBOX/
│   ├── Sent/
│   ├── Drafts/
│   └── Trash/
├── bob@localhost/
│   └── ...
```

## Development Roadmap

- [x] Basic SMTP/POP3/IMAP implementation
- [x] Email-based mailbox directories
- [x] Local domain configuration
- [x] Multi-user authentication
- [ ] Index caching with read/write locks
- [ ] MX mode (unauthenticated local delivery)
- [ ] TLS/SSL support
- [ ] Spam filtering
- [ ] Quota management

## License

MIT License

## Contributing

Contributions are welcome! Please feel free to submit pull requests.
