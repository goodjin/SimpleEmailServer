#!/bin/bash
# Test SMTP - Send email from alice to bob

echo "Testing SMTP: Sending email from alice to bob..."
{
  sleep 1
  echo "EHLO testclient"
  sleep 0.5
  echo "AUTH LOGIN"
  sleep 0.5
  echo "YWxpY2U="  # base64: alice
  sleep 0.5
  echo "cGFzc3dvcmQxMjM="  # base64: password123
  sleep 0.5
  echo "MAIL FROM:<alice@localhost>"
  sleep 0.5
  echo "RCPT TO:<bob@localhost>"
  sleep 0.5
  echo "DATA"
  sleep 0.5
  echo "From: alice@localhost"
  echo "To: bob@localhost"
  echo "Subject: Test Email via New Mailbox System"
  echo ""
  echo "Hello Bob,"
  echo "This is a test email to verify the new mailbox storage system."
  echo "It should be saved to your INBOX folder."
  echo ""
  echo "Best regards,"
  echo "Alice"
  echo "."
  sleep 0.5
  echo "QUIT"
  sleep 1
} | nc localhost 2525

echo ""
echo "SMTP test completed!"
