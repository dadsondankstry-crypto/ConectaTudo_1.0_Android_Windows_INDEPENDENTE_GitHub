"""Interface portátil Windows para ConectaTudo. Sem conta e sem internet externa."""
import http.client
import json
import os
import queue
import socket
import sys
import threading
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, ttk
from urllib.parse import quote
import urllib.request
import webbrowser
from host import DeviceHost, PORT, discover, local_ips

class App:
    def __init__(self, root):
        self.root = root
        root.title('ConectaTudo 1.0 • Windows')
        root.geometry('650x730')
        root.minsize(550, 610)
        self.host = DeviceHost()
        self.messages = queue.Queue()
        self.peer_ip = tk.StringVar()
        self.peer_pin = tk.StringVar()
        self.send_text = tk.StringVar()
        self.screen = tk.BooleanVar(value=False)
        self.status = tk.StringVar(value='Preparando servidor...')
        self.remote_name = tk.StringVar(value='Não conectado')
        self._ui()
        try:
            self.host.start()
            self.refresh_status()
        except OSError as e:
            self.status.set('Não foi possível abrir a porta 8765: ' + str(e))
            messagebox.showerror('ConectaTudo', 'Porta 8765 ocupada. Feche outra cópia do programa.\n' + str(e))
        root.after(100, self._pump)
        root.protocol('WM_DELETE_WINDOW', self.close)

    def _ui(self):
        bg = '#121a2b'
        self.root.configure(bg=bg)
        style = ttk.Style()
        style.theme_use('clam')
        style.configure('TFrame', background=bg)
        style.configure('TLabel', background=bg, foreground='#e8efff', font=('Segoe UI', 10))
        style.configure('Header.TLabel', background=bg, foreground='#6fb0ff', font=('Segoe UI', 17, 'bold'))
        style.configure('TButton', font=('Segoe UI', 10), padding=7)
        style.configure('TEntry', padding=7)
        outer = ttk.Frame(self.root, padding=18); outer.pack(fill='both', expand=True)
        ttk.Label(outer, text='🔗 ConectaTudo • Windows', style='Header.TLabel').pack(anchor='w')
        ttk.Label(outer, text='Rede local • Sem nuvem • Envio de arquivos e tela').pack(anchor='w', pady=(3, 12))
        ttk.Label(outer, text='ESTE COMPUTADOR', font=('Segoe UI', 11, 'bold')).pack(anchor='w')
        self.ips = ttk.Label(outer, text='IP: ...'); self.ips.pack(anchor='w', pady=(6, 2))
        self.pin_label = ttk.Label(outer, text='Código: ...'); self.pin_label.pack(anchor='w')
        ttk.Label(outer, text='Porta: 8765 • Conecte os aparelhos ao mesmo Wi-Fi/hotspot.').pack(anchor='w', pady=(0, 4))
        row = ttk.Frame(outer); row.pack(fill='x', pady=5)
        ttk.Button(row, text='Abrir meu painel no navegador', command=self.open_local).pack(side='left', padx=(0, 5))
        ttk.Button(row, text='Abrir pasta Recebidos', command=self.open_folder).pack(side='left')
        ttk.Checkbutton(outer, text='🖥️ Compartilhar tela deste Windows (com autorização)', variable=self.screen,
                        command=self.toggle_screen).pack(anchor='w', pady=(6, 12))
        ttk.Separator(outer).pack(fill='x', pady=7)
        ttk.Label(outer, text='CONECTAR AO CELULAR OU TABLET', font=('Segoe UI', 11, 'bold')).pack(anchor='w')
        row = ttk.Frame(outer); row.pack(fill='x', pady=5)
        ttk.Label(row, text='IP:').pack(side='left')
        ttk.Entry(row, textvariable=self.peer_ip, width=20).pack(side='left', padx=5, fill='x', expand=True)
        ttk.Label(row, text='Código:').pack(side='left')
        ttk.Entry(row, textvariable=self.peer_pin, width=10).pack(side='left', padx=5)
        row = ttk.Frame(outer); row.pack(fill='x', pady=5)
        ttk.Button(row, text='🔎 Procurar aparelhos', command=self.find_peers).pack(side='left', padx=(0, 6))
        ttk.Button(row, text='Conectar', command=self.connect).pack(side='left', padx=(0, 6))
        ttk.Button(row, text='Abrir painel remoto', command=self.open_remote).pack(side='left')
        ttk.Label(outer, textvariable=self.remote_name).pack(anchor='w', pady=(4, 6))
        row = ttk.Frame(outer); row.pack(fill='x', pady=5)
        ttk.Button(row, text='📤 Enviar arquivos selecionados', command=self.send_files).pack(side='left', padx=(0, 6))
        ttk.Button(row, text='👁️ Ver tela remota', command=self.view_screen).pack(side='left')
        ttk.Label(outer, text='MENSAGEM DE TEXTO').pack(anchor='w', pady=(14, 2))
        row = ttk.Frame(outer); row.pack(fill='x')
        ttk.Entry(row, textvariable=self.send_text).pack(side='left', fill='x', expand=True, padx=(0, 6))
        ttk.Button(row, text='Enviar', command=self.send_message).pack(side='left')
        ttk.Label(outer, text='ATIVIDADE / INSTRUÇÕES').pack(anchor='w', pady=(12, 4))
        self.log = tk.Text(outer, bg='#0b1020', fg='#dbeafe', insertbackground='white', height=8,
                           relief='flat', wrap='word', state='disabled', font=('Consolas', 9))
        self.log.pack(fill='both', expand=True)
        ttk.Label(outer, textvariable=self.status, wraplength=580).pack(anchor='w', pady=(8, 0))
        self._log('Dica: no celular, informe o IP deste Windows e o código acima. Ou faça o contrário.')
        self._log('Sem câmera? Use IP e código. Internet NÃO é necessária quando ambos estão no mesmo hotspot.')

    def _log(self, msg):
        self.log.configure(state='normal')
        self.log.insert('end', msg + '\n')
        self.log.see('end')
        self.log.configure(state='disabled')

    def _pump(self):
        try:
            while True:
                kind, val = self.messages.get_nowait()
                if kind == 'log': self._log(val)
                elif kind == 'status': self.status.set(val)
                elif kind == 'connected': self.remote_name.set('✅ ' + val)
                elif kind == 'found': self._choose_peer(val)
                elif kind == 'error': messagebox.showerror('ConectaTudo', val)
        except queue.Empty:
            pass
        self.root.after(120, self._pump)

    def bg(self, fn):
        threading.Thread(target=fn, daemon=True).start()

    def refresh_status(self):
        self.ips.config(text='Endereço(s): ' + (', '.join(local_ips()) or '(rede não detectada)'))
        self.pin_label.config(text='Código de acesso (PIN): ' + self.host.pin)
        self.status.set('Servidor ativo na porta 8765 • Arquivos recebidos: ' + str(self.host.folder))

    def toggle_screen(self):
        self.host.sharing_screen = self.screen.get()
        self._log('Tela do Windows: ' + ('COMPARTILHAMENTO ATIVADO' if self.screen.get() else 'parado'))

    def open_local(self):
        webbrowser.open(f'http://127.0.0.1:{PORT}/?pin={self.host.pin}')

    def open_folder(self):
        self.host.folder.mkdir(parents=True, exist_ok=True)
        if sys.platform.startswith('win'): os.startfile(str(self.host.folder))
        else: self._log(str(self.host.folder))

    def target(self, path):
        ip = self.peer_ip.get().strip().replace('http://', '').replace('https://', '').split('/')[0].split(':')[0]
        pin = self.peer_pin.get().strip()
        if not ip or len(pin) != 6 or not pin.isdecimal():
            raise ValueError('Digite o IP do outro aparelho e seu código de 6 números.')
        # Bloqueia nomes com prefixos de URL, somente endereço IPv4/hostname local
        if any(x in ip for x in ('@', '?', '#', ' ')):
            raise ValueError('Endereço inválido')
        return ip, pin, f'http://{ip}:{PORT}{path}' + ('&' if '?' in path else '?') + 'pin=' + pin

    def connect(self):
        try: _,_,url = self.target('/api/info')
        except ValueError as e: return messagebox.showwarning('ConectaTudo', str(e))
        def task():
            try:
                with urllib.request.urlopen(url, timeout=7) as response:
                    data = json.load(response)
                self.messages.put(('connected', data.get('name', 'Dispositivo conectado')))
                self.messages.put(('log', 'Conexão confirmada com ' + data.get('name', 'dispositivo')))
            except Exception as e:
                self.messages.put(('error', 'Não conectou. Verifique IP, PIN, hotspot e firewall.\n' + str(e)))
        self.bg(task)

    def find_peers(self):
        self.status.set('Procurando outros dispositivos na mesma rede...')
        self.bg(lambda: self.messages.put(('found', discover(3.2))))

    def _choose_peer(self, data):
        if not data:
            self._log('Nada encontrado na busca automática. Informe IP e código manualmente.'); return
        popup = tk.Toplevel(self.root); popup.title('Dispositivos encontrados'); popup.geometry('440x220')
        lb = tk.Listbox(popup, font=('Segoe UI', 11))
        lb.pack(fill='both', expand=True, padx=10, pady=10)
        for dev in data: lb.insert('end', f"{dev['name']}  —  {dev['ip']}")
        def choose():
            sel = lb.curselection()
            if sel:
                self.peer_ip.set(data[sel[0]]['ip']); popup.destroy()
                self._log('IP preenchido. Digite o PIN mostrado no outro aparelho e clique Conectar.')
        ttk.Button(popup, text='Usar endereço selecionado', command=choose).pack(pady=(0, 8))

    def open_remote(self):
        try: _,_,url = self.target('/')
        except ValueError as e: return messagebox.showwarning('ConectaTudo', str(e))
        webbrowser.open(url)

    def view_screen(self):
        try: _,_,url = self.target('/screen')
        except ValueError as e: return messagebox.showwarning('ConectaTudo', str(e))
        webbrowser.open(url)

    def send_files(self):
        try: ip, pin, _ = self.target('/api/send')
        except ValueError as e: return messagebox.showwarning('ConectaTudo', str(e))
        files = filedialog.askopenfilenames(title='Arquivos para enviar')
        if not files: return
        def task():
            try:
                for file in files:
                    size = os.path.getsize(file)
                    name = Path(file).name
                    path = '/api/send?pin=' + pin + '&name=' + quote(name)
                    conn = http.client.HTTPConnection(ip, PORT, timeout=240)
                    try:
                        conn.putrequest('POST', path)
                        conn.putheader('Content-Type', 'application/octet-stream')
                        conn.putheader('Content-Length', str(size))
                        conn.endheaders()
                        with open(file, 'rb') as f:
                            while chunk := f.read(128 * 1024): conn.send(chunk)
                        res = conn.getresponse()
                        data = res.read(4096)
                        if res.status != 200: raise IOError(str(data[:300]))
                        self.messages.put(('log', '✅ Enviado: ' + name + f' ({size:,} bytes)'))
                    finally: conn.close()
                self.messages.put(('status', 'Transferências concluídas.'))
            except Exception as e:
                self.messages.put(('error', 'Falha na transferência: ' + str(e)))
        self.bg(task)

    def send_message(self):
        msg = self.send_text.get().strip()
        if not msg: return
        try: _,_,url = self.target('/api/message')
        except ValueError as e: return messagebox.showwarning('ConectaTudo', str(e))
        self.send_text.set('')
        def task():
            try:
                req = urllib.request.Request(url, data=msg.encode(), method='POST',
                                             headers={'Content-Type': 'text/plain; charset=utf-8'})
                with urllib.request.urlopen(req, timeout=9) as res: res.read()
                self.messages.put(('log', '✉️ ' + msg[:180]))
            except Exception as e:
                self.messages.put(('error', 'Mensagem não enviada: ' + str(e)))
        self.bg(task)

    def close(self):
        try: self.host.stop()
        except Exception: pass
        self.root.destroy()

if __name__ == '__main__':
    root = tk.Tk(); App(root); root.mainloop()
