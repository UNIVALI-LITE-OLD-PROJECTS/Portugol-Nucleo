package br.univali.portugol.nucleo.execucao.gerador;

import br.univali.portugol.nucleo.execucao.gerador.helpers.Utils;
import br.univali.portugol.nucleo.Programa;
import br.univali.portugol.nucleo.asa.*;
import br.univali.portugol.nucleo.execucao.gerador.helpers.*;
import br.univali.portugol.nucleo.mensagens.ErroExecucao;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Elieser
 */
public class GeradorCodigoJava
{
    private static final String PACOTE_DAS_LIBS = "br.univali.portugol.nucleo.bibliotecas.";

    private final GeradorChamadaMetodo geradorChamadaMetodo = new GeradorChamadaMetodo();
    private final GeradorSwitchCase geradorSwitchCase = new GeradorSwitchCase();
    private final GeradorDeclaracaoMetodo geradorDeclaracaoMetodo = new GeradorDeclaracaoMetodo();
    private final GeradorOperacao geradorOperacao = new GeradorOperacao();
    private final GeradorAtributo geradorAtributo = new GeradorAtributo();
    private final GeradorDeclaracaoVariavel geradorDeclaracaoVariavel = new GeradorDeclaracaoVariavel();
    private final GeradorAtribuicao geradorAtribuicao = new GeradorAtribuicao();

    public void gera(ASAPrograma asa, PrintWriter saida, String nomeClasseJava) throws ExcecaoVisitaASA, IOException
    {
        gera(asa, saida, nomeClasseJava, false);
    }

    public void gera(ASAPrograma asa, PrintWriter saida, String nomeClasseJava, boolean gerandoCodigoParaTesteUnitario) throws ExcecaoVisitaASA, IOException
    {
        BuscadorReferencias buscadorReferencias = new BuscadorReferencias();
        asa.aceitar(buscadorReferencias);

        VisitorGeracaoCodigo gerador = new VisitorGeracaoCodigo(asa, saida, gerandoCodigoParaTesteUnitario);
        gerador.geraPackage("programas")
                .pulaLinha()
                .geraImportacaoPara(ErroExecucao.class)
                .geraImportacaoPara(Programa.class)
                .geraImportacaoDasBibliotecasIncluidas()
                .pulaLinha()
                .geraNomeDaClasse(nomeClasseJava)
                .geraChaveDeAberturaDaClasse()
                .pulaLinha()
                .geraAtributosParaAsBibliotecasIncluidas()
                .pulaLinha()
                .geraAtributosParaAsVariaveisGlobais()
                .pulaLinha()
                .geraAtributosParaAsVariaveisPassadasPorReferencia(buscadorReferencias.getVariaveisPassadasPorReferencia())
                .pulaLinha()
                .geraConstrutor(nomeClasseJava)
                .pulaLinha()
                .geraMetodos()
                .geraChaveDeFechamentoDaClasse();
    }

    private class VisitorGeracaoCodigo extends VisitanteASABasico
    {
        private final PrintWriter saida;
        private final ASAPrograma asa;
        private int nivelEscopo = 1;
        private final boolean gerandoCodigoParaTesteUnitario; // O código gerado para rodar os testes unitários não inclui alguns detalhes relacionados com execução passo a passo e verificação de thread interrompida durante a execução do programa.

        public VisitorGeracaoCodigo(ASAPrograma asa, PrintWriter saida, boolean geraCodigoParaTesteUnitario)
        {
            this.saida = saida;
            this.asa = asa;
            this.gerandoCodigoParaTesteUnitario = geraCodigoParaTesteUnitario;
        }

        private void visitarBlocos(List<NoBloco> blocos) throws ExcecaoVisitaASA
        {
            nivelEscopo++;
            Utils.visitarBlocos(blocos, saida, this, nivelEscopo, gerandoCodigoParaTesteUnitario);
            nivelEscopo--;
        }

        private void geraVerificacaoThreadInterrompida()
        {
            nivelEscopo++;
            Utils.geraVerificacaoThreadInterrompida(saida, nivelEscopo, gerandoCodigoParaTesteUnitario);
            nivelEscopo--;
        }

        public VisitorGeracaoCodigo geraAtributosParaAsVariaveisGlobais() throws ExcecaoVisitaASA
        {
            List<NoDeclaracao> variaveisGlobais = asa.getListaDeclaracoesGlobais();
            boolean existemVariaveisGlobais = false;
            for (NoDeclaracao no : variaveisGlobais)
            {
                boolean atributoGerado = geradorAtributo.gera(no, saida, this, nivelEscopo);
                existemVariaveisGlobais |= atributoGerado;
            }

            if (existemVariaveisGlobais)
            {
                saida.println(); // deixa uma linha em branco depois dos atributos globais
            }

            return this;
        }

        public VisitorGeracaoCodigo geraAtributosParaAsBibliotecasIncluidas()
        {
            List<NoInclusaoBiblioteca> libsIncluidas = asa.getListaInclusoesBibliotecas();
            for (NoInclusaoBiblioteca biblioteca : libsIncluidas)
            {
                geradorAtributo.gera(biblioteca, saida, nivelEscopo);
            }

            if (!libsIncluidas.isEmpty())
            {
                saida.println(); // deixa uma linha em branco depois dos atributos das bibliotecas
            }

            return this;
        }

        public VisitorGeracaoCodigo pulaLinha()
        {
            saida.println();
            return this;
        }

        public VisitorGeracaoCodigo geraPackage(String stringPackage)
        {
            saida.append("package ")
                    .append(stringPackage)
                    .append(";")
                    .println();

            return this;
        }

        public VisitorGeracaoCodigo geraMetodos() throws ExcecaoVisitaASA
        {
            List<NoDeclaracao> declaracoes = asa.getListaDeclaracoesGlobais();
            for (NoDeclaracao declaracao : declaracoes)
            {
                if (declaracao instanceof NoDeclaracaoFuncao)
                {
                    geradorDeclaracaoMetodo.gera((NoDeclaracaoFuncao) declaracao, saida, this, nivelEscopo, gerandoCodigoParaTesteUnitario);
                }
            }
            return this;
        }

        @Override
        public Void visitar(NoInteiro noInteiro) throws ExcecaoVisitaASA
        {
            saida.append(String.valueOf(noInteiro.getValor()));
            return null;
        }

        @Override
        public Void visitar(NoLogico noLogico) throws ExcecaoVisitaASA
        {
            String valor = noLogico.getValor() ? "true" : "false";
            saida.append(valor);
            return null;
        }

        @Override
        public Void visitar(NoCaracter noCaracter) throws ExcecaoVisitaASA
        {
            String valor = "'" + noCaracter.getValor() + "'";
            saida.append(valor);
            return null;
        }

        @Override
        public Void visitar(NoReal noReal) throws ExcecaoVisitaASA
        {
            String valor = String.valueOf(noReal.getValor());
            saida.append(valor);
            return null;
        }

        @Override
        public Void visitar(NoCadeia noCadeia) throws ExcecaoVisitaASA
        {
            String valor = Utils.preservaCaracteresEspeciais(noCadeia.getValor());
            valor = '\"' + valor + '\"';
            saida.append(valor);
            return null;
        }

        @Override
        public Void visitar(NoOperacaoBitwiseLeftShift no) throws ExcecaoVisitaASA
        {
            geradorOperacao.gera(no, saida, this);
            return null;
        }

        @Override
        public Void visitar(NoOperacaoBitwiseRightShift no) throws ExcecaoVisitaASA
        {
            geradorOperacao.gera(no, saida, this);
            return null;
        }

        @Override
        public Void visitar(NoOperacaoBitwiseE no) throws ExcecaoVisitaASA
        {
            geradorOperacao.gera(no, saida, this);
            return null;
        }

        @Override
        public Void visitar(NoOperacaoBitwiseXOR no) throws ExcecaoVisitaASA
        {
            geradorOperacao.gera(no, saida, this);
            return null;
        }

        @Override
        public Void visitar(NoOperacaoBitwiseOu no) throws ExcecaoVisitaASA
        {
            geradorOperacao.gera(no, saida, this);
            return null;
        }

        @Override
        public Void visitar(NoOperacaoSoma no) throws ExcecaoVisitaASA
        {
            geradorOperacao.gera(no, saida, this);
            return null;
        }

        @Override
        public Void visitar(NoOperacaoDivisao no) throws ExcecaoVisitaASA
        {
            geradorOperacao.gera(no, saida, this);
            return null;
        }

        @Override
        public Void visitar(NoOperacaoModulo no) throws ExcecaoVisitaASA
        {
            geradorOperacao.gera(no, saida, this);
            return null;
        }

        @Override
        public Void visitar(NoOperacaoSubtracao no) throws ExcecaoVisitaASA
        {
            geradorOperacao.gera(no, saida, this);
            return null;
        }

        @Override
        public Void visitar(NoOperacaoMultiplicacao no) throws ExcecaoVisitaASA
        {
            geradorOperacao.gera(no, saida, this);
            return null;
        }

        @Override
        public Void visitar(NoOperacaoLogicaOU no) throws ExcecaoVisitaASA
        {
            geradorOperacao.gera(no, saida, this);
            return null;
        }

        @Override
        public Void visitar(NoOperacaoLogicaE no) throws ExcecaoVisitaASA
        {
            geradorOperacao.gera(no, saida, this);
            return null;
        }

        @Override
        public Void visitar(NoOperacaoLogicaDiferenca no) throws ExcecaoVisitaASA
        {
            geradorOperacao.gera(no, saida, this);
            return null;
        }

        @Override
        public Void visitar(NoOperacaoLogicaIgualdade no) throws ExcecaoVisitaASA
        {
            geradorOperacao.gera(no, saida, this);
            return null;
        }

        @Override
        public Void visitar(NoOperacaoLogicaMaior no) throws ExcecaoVisitaASA
        {
            geradorOperacao.gera(no, saida, this);
            return null;
        }

        @Override
        public Void visitar(NoOperacaoLogicaMaiorIgual no) throws ExcecaoVisitaASA
        {
            geradorOperacao.gera(no, saida, this);
            return null;
        }

        @Override
        public Void visitar(NoOperacaoLogicaMenor no) throws ExcecaoVisitaASA
        {
            geradorOperacao.gera(no, saida, this);
            return null;
        }

        @Override
        public Void visitar(NoOperacaoLogicaMenorIgual no) throws ExcecaoVisitaASA
        {
            geradorOperacao.gera(no, saida, this);
            return null;
        }

        @Override
        public Void visitar(NoDeclaracaoVariavel noDeclaracao) throws ExcecaoVisitaASA
        {
            geradorDeclaracaoVariavel.gera(noDeclaracao, saida, this, nivelEscopo);
            return null;
        }

        @Override
        public Void visitar(NoDeclaracaoVetor no) throws ExcecaoVisitaASA
        {
            geradorDeclaracaoVariavel.gera(no, saida, this, nivelEscopo);
            return null;
        }

        @Override
        public Void visitar(NoVetor noVetor) throws ExcecaoVisitaASA
        {
            geradorDeclaracaoVariavel.gera(noVetor, saida, this, nivelEscopo);
            return null;
        }

        @Override
        public Void visitar(NoDeclaracaoMatriz noDeclaracao) throws ExcecaoVisitaASA
        {
            geradorDeclaracaoVariavel.gera(noDeclaracao, saida, this, nivelEscopo);
            return null;
        }

        @Override
        public Void visitar(NoMatriz noMatriz) throws ExcecaoVisitaASA
        {
            geradorDeclaracaoVariavel.gera(noMatriz, saida, this, nivelEscopo);
            return null;
        }

        @Override
        public Void visitar(NoRetorne no) throws ExcecaoVisitaASA
        {
            NoExpressao expressao = no.getExpressao();
            if (expressao != null)
            {
                saida.append("return ");
                if (no.temPai())
                {

                    if (no.getPai() instanceof NoDeclaracaoFuncao)
                    {
                        TipoDado tipoRetornoFuncao = ((NoDeclaracaoFuncao) no.getPai()).getTipoDado();
                        if (expressao.getTipoResultante() == TipoDado.REAL && tipoRetornoFuncao == TipoDado.INTEIRO)
                        {
                            saida.append("(int)");
                        }
                    }
                }
                else
                {
                    throw new IllegalStateException("retorne não tem pai!");
                }

                expressao.aceitar(this);
            }
            else
            {
                saida.append("return");
            }

            return null;
        }

        @Override
        public Void visitar(NoReferenciaVetor no) throws ExcecaoVisitaASA
        {
            saida.append(Utils.geraNomeValido(no.getNome()));

            saida.append("[");
            no.getIndice().aceitar(this);
            saida.append("]");

            return null;
        }

        @Override
        public Void visitar(NoReferenciaMatriz no) throws ExcecaoVisitaASA
        {
            saida.append(Utils.geraNomeValido(no.getNome()))
                    .append("[");

            no.getLinha().aceitar(this);

            saida.append("][");

            no.getColuna().aceitar(this);

            saida.append("]");

            return null;
        }

        @Override
        public Void visitar(NoReferenciaVariavel no) throws ExcecaoVisitaASA
        {
            String nome = Utils.geraNomeValido(no.getNome());
            if (no.getEscopo() != null)
            {
                saida.append(no.getEscopo())
                        .append(".");
            }
            
            NoDeclaracao declaracao = no.getOrigemDaReferencia();
            boolean ehParametroPorReferencia = declaracao instanceof NoDeclaracaoParametro && (((NoDeclaracaoParametro)declaracao).getModoAcesso() == ModoAcesso.POR_REFERENCIA);
            if(ehParametroPorReferencia || no.ehPassadoPorReferencia())
            {
                String stringIndice = ehParametroPorReferencia ? no.getNome() : ("INDICE_" + no.getNome().toUpperCase());
                saida.format("REFERENCIAS[%s]", stringIndice);
            }
            else
            {
                saida.append(nome);
            }

            return null;
        }

        @Override
        public Void visitar(NoEnquanto no) throws ExcecaoVisitaASA
        {

            saida.append("while(");

            no.getCondicao().aceitar(this);

            saida.append(")").println();

            String identacao = Utils.geraIdentacao(nivelEscopo);

            saida.append(identacao).append("{").println();

            geraVerificacaoThreadInterrompida();

            visitarBlocos(no.getBlocos());

            saida.println();

            saida.append(identacao).append("}").println();

            return null;
        }

        @Override
        public Void visitar(NoPara no) throws ExcecaoVisitaASA
        {
            saida.append("for(");
            if (no.getInicializacao() != null)
            {
                no.getInicializacao().aceitar(this);
            }

            saida.append("; "); // separador depois da inicialização do for 

            no.getCondicao().aceitar(this);

            saida.append("; "); // separador depois da c

            if (no.getIncremento() != null)
            {
                no.getIncremento().aceitar(this);
            }

            saida.append(")").println(); // fecha o parênteses do for

            String identacao = Utils.geraIdentacao(nivelEscopo);

            saida.append(identacao).append("{").println();

            geraVerificacaoThreadInterrompida();

            visitarBlocos(no.getBlocos());

            saida.println();

            saida.append(identacao).append("}").println();

            return null;
        }

        @Override
        public Void visitar(NoSe no) throws ExcecaoVisitaASA
        {
            saida.append("if(");

            no.getCondicao().aceitar(this);

            saida.append(")").println();

            String identacao = Utils.geraIdentacao(nivelEscopo);

            saida.append(identacao).append("{").println();

            List<NoBloco> blocosVerdadeiros = no.getBlocosVerdadeiros();
            if (blocosVerdadeiros != null)
            {
                visitarBlocos(blocosVerdadeiros);
                saida.println();
            }

            saida.append(identacao).append("}").println();

            List<NoBloco> blocosFalsos = no.getBlocosFalsos();
            if (blocosFalsos != null)
            {
                saida.append(identacao).append("else").println();
                saida.append(identacao).append("{").println();

                visitarBlocos(blocosFalsos);

                saida.println();

                saida.append(identacao).append("}").println();
            }

            return null;
        }

        private boolean simularBreakCaso = false;

        @Override
        public Void visitar(NoEscolha no) throws ExcecaoVisitaASA
        {
            boolean contemCasosNaoConstantes = GeradorSwitchCase.contemCasosNaoConstantes(no);
            simularBreakCaso = contemCasosNaoConstantes;

            if (!contemCasosNaoConstantes)
            {
                geradorSwitchCase.geraSwitchCase(no, saida, this, nivelEscopo, gerandoCodigoParaTesteUnitario);
            }
            else
            {
                geradorSwitchCase.geraSeSenao(no, saida, this, nivelEscopo, gerandoCodigoParaTesteUnitario);
            }

            return null;
        }

        @Override
        public Void visitar(NoFacaEnquanto no) throws ExcecaoVisitaASA
        {
            String identacao = Utils.geraIdentacao(nivelEscopo);

            saida.append("do").println();
            saida.append(identacao).append("{").println();

            geraVerificacaoThreadInterrompida();

            List<NoBloco> blocos = no.getBlocos();
            if (blocos != null)
            {
                visitarBlocos(blocos);
                saida.println();
            }

            saida.append(identacao).append("}").println();

            saida.append(identacao).append("while(");

            no.getCondicao().aceitar(this);

            saida.append(");").println();

            return null;
        }

        @Override
        public Void visitar(NoOperacaoAtribuicao no) throws ExcecaoVisitaASA
        {
            geradorAtribuicao.gera(no, saida, this);
            return null;
        }

        @Override
        public Void visitar(NoMenosUnario no) throws ExcecaoVisitaASA
        {
            saida.append("-");
            no.getExpressao().aceitar(this);

            return null;
        }

        @Override
        public Void visitar(NoNao no) throws ExcecaoVisitaASA
        {
            saida.append("!");
            no.getExpressao().aceitar(this);

            return null;
        }

        @Override
        public Void visitar(NoChamadaFuncao no) throws ExcecaoVisitaASA
        {
            geradorChamadaMetodo.gera(no, saida, this, asa);
            return null;
        }

        @Override
        public Void visitar(NoPare noPare) throws ExcecaoVisitaASA
        {
            if (simularBreakCaso)
            {
                saida.append(GeradorSwitchCase.NOME_VARIAVEL_BREAK)
                        .append(" = true");
            }
            else
            {
                saida.append("break");
            }

            return null;
        }

        @Override
        public Object visitar(NoBitwiseNao no) throws ExcecaoVisitaASA
        {
            saida.append("~");
            no.getExpressao().aceitar(this);

            return null;
        }

        @Override
        public Object visitar(NoContinue noContinue) throws ExcecaoVisitaASA
        {
            saida.append("continue");

            return null;
        }

        public VisitorGeracaoCodigo geraImportacaoPara(Class classe)
        {
            saida.append("import ")
                    .append(classe.getCanonicalName())
                    .append(";")
                    .println();

            return this;
        }

        private VisitorGeracaoCodigo geraImportacaoDasBibliotecasIncluidas()
        {
            for (NoInclusaoBiblioteca no : asa.getListaInclusoesBibliotecas())
            {
                saida.append("import ")
                        .append(PACOTE_DAS_LIBS)
                        .append(no.getNome())
                        .append(";")
                        .println();

            }

            return this;
        }

        private VisitorGeracaoCodigo geraConstrutor(String nomeDaClasseJava)
        {
            saida.append(Utils.geraIdentacao(nivelEscopo))
                    .append("public ")
                    .append(nomeDaClasseJava)
                    .append("() throws ErroExecucao, InterruptedException {");

            saida.append("}").println();

            return this;
        }

        private VisitorGeracaoCodigo geraNomeDaClasse(String nomeClasseJava)
        {
            saida.format("public class %s extends Programa", nomeClasseJava).println();

            return this;
        }

        public VisitorGeracaoCodigo geraChaveDeAberturaDaClasse()
        {
            saida.append("{").println();

            return this;
        }

        public VisitorGeracaoCodigo geraChaveDeFechamentoDaClasse()
        {
            saida.append("}").println();

            return this;
        }

        public VisitorGeracaoCodigo geraAtributosParaAsVariaveisPassadasPorReferencia(List<NoDeclaracaoVariavel> variaveis)
        {
            if (variaveis.isEmpty())
            {
                return this;
            }

            String identacao = Utils.geraIdentacao(nivelEscopo);

            //declara o array que armazena todas as referências
            saida.append(identacao)
                    .format("private final int[] REFERENCIAS = new int[%d];", variaveis.size());
            saida.println();

            for (NoDeclaracaoVariavel variavel : variaveis)
            {
                saida.append(identacao)
                        .append("private final int ")
                        .append("INDICE_" + variavel.getNome().toUpperCase())
                        .append(" = ")
                        .append(String.valueOf(variavel.getIndiceReferencia()))
                        .append(";")
                        .println();
            }
            return this;
        }

    }

}