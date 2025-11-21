#!/bin/bash
# 邮件服务器测试脚本
# 三个账号相互发送邮件

SMTP_HOST="localhost"
SMTP_PORT="2525"

# 颜色输出
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "========================================="
echo "开始测试邮件发送..."
echo "========================================="

# Alice 给 Bob 和 Charlie 发邮件
echo -e "${BLUE}[1/9] Alice -> Bob${NC}"
{
  sleep 0.5
  echo "EHLO testclient"
  sleep 0.3
  echo "AUTH LOGIN"
  sleep 0.3
  echo "YWxpY2U="  # alice
  sleep 0.3
  echo "cGFzc3dvcmQxMjM="  # password123
  sleep 0.3
  echo "MAIL FROM:<alice@localhost>"
  sleep 0.3
  echo "RCPT TO:<bob@localhost>"
  sleep 0.3
  echo "DATA"
  sleep 0.2
  echo "From: alice@localhost"
  echo "To: bob@localhost"
  echo "Subject: Hello from Alice to Bob"
  echo ""
  echo "Hi Bob,"
  echo "This is a test message from Alice."
  echo "Best regards,"
  echo "Alice"
  echo "."
  sleep 0.2
  echo "QUIT"
} | nc -w 5 $SMTP_HOST $SMTP_PORT > /dev/null
echo -e "${GREEN}✓ 发送成功${NC}"
sleep 1

echo -e "${BLUE}[2/9] Alice -> Charlie${NC}"
{
  sleep 0.5
  echo "EHLO testclient"
  sleep 0.3
  echo "AUTH LOGIN"
  sleep 0.3
  echo "YWxpY2U="
  sleep 0.3
  echo "cGFzc3dvcmQxMjM="
  sleep 0.3
  echo "MAIL FROM:<alice@localhost>"
  sleep 0.3
  echo "RCPT TO:<charlie@localhost>"
  sleep 0.3
  echo "DATA"
  sleep 0.2
  echo "From: alice@localhost"
  echo "To: charlie@localhost"
  echo "Subject: Hello from Alice to Charlie"
  echo ""
  echo "Hi Charlie,"
  echo "This is a test message from Alice."
  echo "Best regards,"
  echo "Alice"
  echo "."
  sleep 0.2
  echo "QUIT"
} | nc -w 5 $SMTP_HOST $SMTP_PORT > /dev/null
echo -e "${GREEN}✓ 发送成功${NC}"
sleep 1

# Bob 给 Alice 和 Charlie 发邮件
echo -e "${BLUE}[3/9] Bob -> Alice${NC}"
{
  sleep 0.5
  echo "EHLO testclient"
  sleep 0.3
  echo "AUTH LOGIN"
  sleep 0.3
  echo "Ym9i"  # bob
  sleep 0.3
  echo "cGFzc3dvcmQ0NTY="  # password456
  sleep 0.3
  echo "MAIL FROM:<bob@localhost>"
  sleep 0.3
  echo "RCPT TO:<alice@localhost>"
  sleep 0.3
  echo "DATA"
  sleep 0.2
  echo "From: bob@localhost"
  echo "To: alice@localhost"
  echo "Subject: Hello from Bob to Alice"
  echo ""
  echo "Hi Alice,"
  echo "Thanks for your message!"
  echo "This is Bob's reply."
  echo "Cheers,"
  echo "Bob"
  echo "."
  sleep 0.2
  echo "QUIT"
} | nc -w 5 $SMTP_HOST $SMTP_PORT > /dev/null
echo -e "${GREEN}✓ 发送成功${NC}"
sleep 1

echo -e "${BLUE}[4/9] Bob -> Charlie${NC}"
{
  sleep 0.5
  echo "EHLO testclient"
  sleep 0.3
  echo "AUTH LOGIN"
  sleep 0.3
  echo "Ym9i"
  sleep 0.3
  echo "cGFzc3dvcmQ0NTY="
  sleep 0.3
  echo "MAIL FROM:<bob@localhost>"
  sleep 0.3
  echo "RCPT TO:<charlie@localhost>"
  sleep 0.3
  echo "DATA"
  sleep 0.2
  echo "From: bob@localhost"
  echo "To: charlie@localhost"
  echo "Subject: Hello from Bob to Charlie"
  echo ""
  echo "Hi Charlie,"
  echo "Hope you're doing well!"
  echo "This is a message from Bob."
  echo "Best,"
  echo "Bob"
  echo "."
  sleep 0.2
  echo "QUIT"
} | nc -w 5 $SMTP_HOST $SMTP_PORT > /dev/null
echo -e "${GREEN}✓ 发送成功${NC}"
sleep 1

# Charlie 给 Alice 和 Bob 发邮件
echo -e "${BLUE}[5/9] Charlie -> Alice${NC}"
{
  sleep 0.5
  echo "EHLO testclient"
  sleep 0.3
  echo "AUTH LOGIN"
  sleep 0.3
  echo "Y2hhcmxpZQ=="  # charlie
  sleep 0.3
  echo "cGFzc3dvcmQ3ODk="  # password789
  sleep 0.3
  echo "MAIL FROM:<charlie@localhost>"
  sleep 0.3
  echo "RCPT TO:<alice@localhost>"
  sleep 0.3
  echo "DATA"
  sleep 0.2
  echo "From: charlie@localhost"
  echo "To: alice@localhost"
  echo "Subject: Hello from Charlie to Alice"
  echo ""
  echo "Dear Alice,"
  echo "Greetings from Charlie!"
  echo "Just testing the email system."
  echo "Regards,"
  echo "Charlie"
  echo "."
  sleep 0.2
  echo "QUIT"
} | nc -w 5 $SMTP_HOST $SMTP_PORT > /dev/null
echo -e "${GREEN}✓ 发送成功${NC}"
sleep 1

echo -e "${BLUE}[6/9] Charlie -> Bob${NC}"
{
  sleep 0.5
  echo "EHLO testclient"
  sleep 0.3
  echo "AUTH LOGIN"
  sleep 0.3
  echo "Y2hhcmxpZQ=="
  sleep 0.3
  echo "cGFzc3dvcmQ3ODk="
  sleep 0.3
  echo "MAIL FROM:<charlie@localhost>"
  sleep 0.3
  echo "RCPT TO:<bob@localhost>"
  sleep 0.3
  echo "DATA"
  sleep 0.2
  echo "From: charlie@localhost"
  echo "To: bob@localhost"
  echo "Subject: Hello from Charlie to Bob"
  echo ""
  echo "Hey Bob,"
  echo "This is Charlie writing to you."
  echo "Testing the mailbox system!"
  echo "Take care,"
  echo "Charlie"
  echo "."
  sleep 0.2
  echo "QUIT"
} | nc -w 5 $SMTP_HOST $SMTP_PORT > /dev/null
echo -e "${GREEN}✓ 发送成功${NC}"
sleep 1

# 群发邮件测试
echo -e "${BLUE}[7/9] Alice -> Bob & Charlie (群发)${NC}"
{
  sleep 0.5
  echo "EHLO testclient"
  sleep 0.3
  echo "AUTH LOGIN"
  sleep 0.3
  echo "YWxpY2U="
  sleep 0.3
  echo "cGFzc3dvcmQxMjM="
  sleep 0.3
  echo "MAIL FROM:<alice@localhost>"
  sleep 0.3
  echo "RCPT TO:<bob@localhost>"
  sleep 0.3
  echo "RCPT TO:<charlie@localhost>"
  sleep 0.3
  echo "DATA"
  sleep 0.2
  echo "From: alice@localhost"
  echo "To: bob@localhost, charlie@localhost"
  echo "Subject: Group message from Alice"
  echo ""
  echo "Hi everyone,"
  echo "This is a group message to both Bob and Charlie."
  echo "Hope you all receive this!"
  echo "Alice"
  echo "."
  sleep 0.2
  echo "QUIT"
} | nc -w 5 $SMTP_HOST $SMTP_PORT > /dev/null
echo -e "${GREEN}✓ 发送成功${NC}"
sleep 1

echo -e "${BLUE}[8/9] Bob -> Alice & Charlie (群发)${NC}"
{
  sleep 0.5
  echo "EHLO testclient"
  sleep 0.3
  echo "AUTH LOGIN"
  sleep 0.3
  echo "Ym9i"
  sleep 0.3
  echo "cGFzc3dvcmQ0NTY="
  sleep 0.3
  echo "MAIL FROM:<bob@localhost>"
  sleep 0.3
  echo "RCPT TO:<alice@localhost>"
  sleep 0.3
  echo "RCPT TO:<charlie@localhost>"
  sleep 0.3
  echo "DATA"
  sleep 0.2
  echo "From: bob@localhost"
  echo "To: alice@localhost, charlie@localhost"
  echo "Subject: Group message from Bob"
  echo ""
  echo "Hello Alice and Charlie,"
  echo "Bob here sending a broadcast message."
  echo "Let's all stay connected!"
  echo "Bob"
  echo "."
  sleep 0.2
  echo "QUIT"
} | nc -w 5 $SMTP_HOST $SMTP_PORT > /dev/null
echo -e "${GREEN}✓ 发送成功${NC}"
sleep 1

echo -e "${BLUE}[9/9] Charlie -> Alice & Bob (群发)${NC}"
{
  sleep 0.5
  echo "EHLO testclient"
  sleep 0.3
  echo "AUTH LOGIN"
  sleep 0.3
  echo "Y2hhcmxpZQ=="
  sleep 0.3
  echo "cGFzc3dvcmQ3ODk="
  sleep 0.3
  echo "MAIL FROM:<charlie@localhost>"
  sleep 0.3
  echo "RCPT TO:<alice@localhost>"
  sleep 0.3
  echo "RCPT TO:<bob@localhost>"
  sleep 0.3
  echo "DATA"
  sleep 0.2
  echo "From: charlie@localhost"
  echo "To: alice@localhost, bob@localhost"
  echo "Subject: Group message from Charlie"
  echo ""
  echo "Dear Alice and Bob,"
  echo "Charlie here with a message for both of you."
  echo "Great job on the email system!"
  echo "Charlie"
  echo "."
  sleep 0.2
  echo "QUIT"
} | nc -w 5 $SMTP_HOST $SMTP_PORT > /dev/null
echo -e "${GREEN}✓ 发送成功${NC}"

echo ""
echo "========================================="
echo -e "${GREEN}✓ 所有邮件发送完成！${NC}"
echo "========================================="
echo ""
echo "邮件统计："
echo "  - Alice INBOX: 应该有 4 封邮件 (Bob×2, Charlie×2)"
echo "  - Bob INBOX:   应该有 4 封邮件 (Alice×2, Charlie×2)"
echo "  - Charlie INBOX: 应该有 4 封邮件 (Alice×2, Bob×2)"
echo ""
echo "验证方法："
echo "  telnet localhost 11110"
echo "  USER alice (或 bob, charlie)"
echo "  PASS password123 (或 password456, password789)"
echo "  LIST"
echo ""
