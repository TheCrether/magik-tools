import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import * as vscodeLanguageClient from 'vscode-languageclient/node';

import { getJavaExec } from './common';
import { MAGIK_TOOLS_VERSION } from './const';
import { MagikSessionProvider } from './magik-session/magik-session-provider';
import * as net from 'net';


export class MagikLanguageClient implements vscode.Disposable {

	private _context: vscode.ExtensionContext;
	private _client: vscodeLanguageClient.LanguageClient;
	private _magikSessionProvider: MagikSessionProvider | undefined;

	constructor(context: vscode.ExtensionContext) {
		this._context = context;

		this.registerCommands();
	}

	public get magikSessionProvider(): MagikSessionProvider {
		return this._magikSessionProvider;
	}

	public set magikSessionProvider(magikSessionProvider: MagikSessionProvider) {
		if (this._magikSessionProvider) {
			throw new Error('Illegal state');
		}

		this._magikSessionProvider = magikSessionProvider;
	}

	public dispose(): void {
		// Nop.
	}

	public start(): void {
		const javaExec = getJavaExec();
		if (javaExec === null) {
			vscode.window.showWarningMessage('Could locate java executable, either set Java Home setting ("magik.javaHome") or JAVA_HOME environment variable.');
			return;
		}
		const jar = path.join(this._context.extensionPath, 'server', 'magik-language-server-' + MAGIK_TOOLS_VERSION + '.jar');
		const javaDebuggerOptions = '-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,quiet=y,address=5005';

		let serverOptions: vscodeLanguageClient.ServerOptions = {
			run: {
				command: javaExec.toString(),
				args: ['-jar', jar],
				transport: vscodeLanguageClient.TransportKind.stdio
			},
			debug: {
				command: javaExec.toString(),
				args: [javaDebuggerOptions, '-jar', jar],
				transport: vscodeLanguageClient.TransportKind.stdio
			}
		};

		if (this._context.extensionMode !== vscode.ExtensionMode.Production) {
			serverOptions.run.args.push('--debug');
			serverOptions.debug.args.push('--debug');
		}

		if (this._context.extensionMode === vscode.ExtensionMode.Development) {
			serverOptions = (): Promise<vscodeLanguageClient.StreamInfo> => {
				let socket = net.connect({ port: 5007 });
				return Promise.resolve({
					writer: socket,
					reader: socket
				});
			};
		}

		const clientOptions: vscodeLanguageClient.LanguageClientOptions = {
			documentSelector: [{ scheme: 'file', language: 'magik' }],
			synchronize: {
				fileEvents: [
					// Include all files (and directories) and filter in the language server itself.
					vscode.workspace.createFileSystemWatcher('**/*')
				],
				configurationSection: 'magik',
			}
		};

		this._client = new vscodeLanguageClient.LanguageClient(
			'magik',
			'Magik Language Server',
			serverOptions,
			clientOptions
		);

		this._client.start();
	}

	private registerCommands(): void {
		const reIndex = vscode.commands.registerCommand('magik.custom.reIndex', () => this.command_custom_re_index());
		this._context.subscriptions.push(reIndex);
	}

	public stop(): Thenable<void> {
		return this._client.stop();
	}

	// public sendRequest<R>(request: string): Promise<R> {
	// 	return this._client.sendRequest(request);
	// }
	public sendRequest<R>(method: string, param?: any, token?: vscode.CancellationToken): Promise<R> {
		return this._client.sendRequest(method, param, token);
	}

	public sendToSession(text: string, sourcePath?: fs.PathLike): void {
		this._magikSessionProvider.sendToSession(text, sourcePath);
	}

	//#region: Commands
	private command_custom_re_index(): void {
		this._client.sendRequest('custom/reIndex');
	}
	//#endregion

}
