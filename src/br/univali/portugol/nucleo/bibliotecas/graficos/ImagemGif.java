/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.univali.portugol.nucleo.bibliotecas.graficos;

import br.univali.portugol.nucleo.bibliotecas.base.ErroExecucaoBiblioteca;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author noschang
 */
public final class ImagemGif extends Imagem
{
    private static final int TAMANHO_BUFFER = 131072; // 128 KB
    
    private ImageReader leitorGif;
    private MetadadosGif metadados;
    
    private byte[] bytesImagem;

    private ImagemGif()
    {

    }
    
    public ImagemGif(File arquivo) throws ErroExecucaoBiblioteca
    {
        try
        {
            bytesImagem = copiarParaMemoria(arquivo);

             imprimirInformacoesGif(bytesImagem);

            leitorGif = criarLeitorGif(bytesImagem);     
            metadados = lerMetadadosGif();

            avancarQuadro();
        }
        catch (IOException ex)
        {
            throw new ErroExecucaoBiblioteca("Erro ao carregar a imagem GIF");
        }
        
    }    
    
    @Override
    public BufferedImage getImagem() throws ErroExecucaoBiblioteca
    {
        long tempoDesdeUltimoDesenho = System.currentTimeMillis() - metadados.instanteUltimoDesenho;
        int intervaloAtual = metadados.informacoesQuadros[metadados.indiceQuadroAtual].intervalo * 10;
        if (tempoDesdeUltimoDesenho >= intervaloAtual)
        {
            avancarQuadro();
            metadados.instanteUltimoDesenho = System.currentTimeMillis();
        }
        else if (metadados.instanteUltimoDesenho == 0)
        {
           metadados.instanteUltimoDesenho = System.currentTimeMillis();
        }
        
        return metadados.quadroAtual;
    }

    public void avancarQuadro() throws ErroExecucaoBiblioteca
    {
        metadados.indiceQuadroAtual = (metadados.indiceQuadroAtual + 1) % (metadados.numeroQuadros);
        metadados.quadroAtual = lerQuadro(metadados.indiceQuadroAtual);        
    }

    public int getLargura()
    {
        return metadados.largura;
    }

    public int getAltura()
    {
        return metadados.altura;
    }

    public int getNumeroFrames()
    {
        return metadados.numeroQuadros;
    }

    public Color getCorFundo()
    {
        return metadados.corFundo;
    }
    
    public int getIndiceQuadroAtual()
    {
        return metadados.indiceQuadroAtual;
    }
    
    public void irParaQuadro(int indice) throws ErroExecucaoBiblioteca
    {
        if (indice > metadados.numeroQuadros - 1)
            throw new ErroExecucaoBiblioteca("O índice do quadro é invalido. Esta imagem GIF possui apenas " + metadados.numeroQuadros + " quadros");
        
        metadados.indiceQuadroAtual = indice;
        metadados.quadroAtual = lerQuadro(indice);
    }
    
    public BufferedImage getQuadroAtual()
    {
        return metadados.quadroAtual;
    }
    
    public int getIntervalo()
    {
        return metadados.informacoesQuadros[metadados.indiceQuadroAtual].intervalo;
    }
    
    public BufferedImage getQuadro(int indice)
    {
        return lerQuadro(indice);
    }
    
    private ImageReader criarLeitorGif(byte[] bytesImagem) throws IOException
    {
        ImageReader leitor = (ImageReader) ImageIO.getImageReadersByFormatName("gif").next();
        leitor.setInput(ImageIO.createImageInputStream(new ByteArrayInputStream(bytesImagem)));
        
        return leitor;
    }
    
    private byte[] copiarParaMemoria(File arquivo) throws IOException
    {
        int bytesLidos;
        byte[] buffer = new byte[TAMANHO_BUFFER];
        
        try (FileInputStream is = new FileInputStream(arquivo); ByteArrayOutputStream baos = new ByteArrayOutputStream(TAMANHO_BUFFER))
        {
            while ((bytesLidos = is.read(buffer, 0, buffer.length)) > 0)
            {
                baos.write(buffer, 0, bytesLidos);
            }
            
            return baos.toByteArray();
        }
    }
    
    private void imprimirInformacoesGif(byte[] bytesImagem)
    {
        String unit = "bytes";
        double length = bytesImagem.length;
        
        if (length > 1024)
        {
            length = length / 1024.0;
            unit = "kylobytes";
        }
        
        if (length > 1024)
        {
            length = length / 1024.0;
            unit = "megabytes";
        }
        
        if (length > 1024)
        {
            length = length / 1024.0;
            unit = "gigabytes";
        }
        
        System.out.println("GIF carregado, tamanho em memória: " + length + " " + unit);
    }
    
    private MetadadosGif lerMetadadosGif() throws IOException
    {
        MetadadosGif metadadosGif = new MetadadosGif(leitorGif.getNumImages(true));
        
        IIOMetadata metadata = leitorGif.getStreamMetadata();
        
        if (metadata != null)
        {
            IIOMetadataNode globalRoot = (IIOMetadataNode) metadata.getAsTree(metadata.getNativeMetadataFormatName());
            NodeList globalColorTable = globalRoot.getElementsByTagName("GlobalColorTable");
            NodeList globalScreeDescriptor = globalRoot.getElementsByTagName("LogicalScreenDescriptor");
            
            if (globalScreeDescriptor != null && globalScreeDescriptor.getLength() > 0)
            {
                IIOMetadataNode screenDescriptor = (IIOMetadataNode) globalScreeDescriptor.item(0);
                
                if (screenDescriptor != null)
                {
                    metadadosGif.largura = Integer.parseInt(screenDescriptor.getAttribute("logicalScreenWidth"));
                    metadadosGif.altura = Integer.parseInt(screenDescriptor.getAttribute("logicalScreenHeight"));
                }
            }
            
            if (globalColorTable != null && globalColorTable.getLength() > 0)
            {
                IIOMetadataNode colorTable = (IIOMetadataNode) globalColorTable.item(0);
                
                if (colorTable != null)
                {
                    String bgIndex = colorTable.getAttribute("backgroundColorIndex");
                    IIOMetadataNode colorEntry = (IIOMetadataNode) colorTable.getFirstChild();
                    
                    while (colorEntry != null)
                    {
                        if (colorEntry.getAttribute("index").equals(bgIndex))
                        {
                            int red = Integer.parseInt(colorEntry.getAttribute("red"));
                            int green = Integer.parseInt(colorEntry.getAttribute("green"));
                            int blue = Integer.parseInt(colorEntry.getAttribute("blue"));
                            
                            metadadosGif.corFundo = new Color(red, green, blue);
                            
                            break;
                        }
                        colorEntry = (IIOMetadataNode) colorEntry.getNextSibling();
                    }
                }
            }
        }
        
        metadadosGif.quadroErro = new BufferedImage(metadadosGif.largura, metadadosGif.altura, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = metadadosGif.quadroErro.createGraphics();
        
        g.setColor(Color.YELLOW);
        g.fillRect(0, 0, metadadosGif.largura, metadadosGif.altura);
        
        g.dispose();
        
        metadadosGif.quadroErro = Utils.criarImagemCompativel(metadadosGif.quadroErro, false);
        
        return metadadosGif;
    }
    
    private BufferedImage lerQuadro(int frameIndex)
    {        
        boolean hasBackround = false;

        BufferedImage image = null;

        while (image == null && frameIndex < metadados.numeroQuadros)
        {
            try
            {
                image = leitorGif.read(frameIndex);
            }
            catch (IOException | ArrayIndexOutOfBoundsException ex)
            {
                frameIndex++;
            }                
        }

        if (image == null)
        {
            image = metadados.quadroErro;
            
            if (frameIndex > metadados.numeroQuadros - 1)
                frameIndex = metadados.numeroQuadros - 1;
        }
        
        if (metadados.largura == -1 || metadados.altura == -1)
        {
            metadados.largura = image.getWidth();
            metadados.altura = image.getHeight();
        }

        InformacoesQuadro quadro = metadados.informacoesQuadros[frameIndex];
        
        quadro.largura = image.getWidth();
        quadro.altura = image.getHeight();
            
        try
        {
            IIOMetadataNode root = (IIOMetadataNode) leitorGif.getImageMetadata(frameIndex).getAsTree("javax_imageio_gif_image_1.0");
            IIOMetadataNode gce = (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension").item(0);
            NodeList children = root.getChildNodes();

            String disposal = gce.getAttribute("disposalMethod");
            int intervalo = Integer.valueOf(gce.getAttribute("delayTime"));                
        
            if(intervalo==0)
            {
                intervalo=10;
            }
        
            quadro.intervalo = intervalo;
            quadro.disposicao = disposal;            

            if (metadados.master != null)
            {
                int x = 0;
                int y = 0;

                for (int nodeIndex = 0; nodeIndex < children.getLength(); nodeIndex++)
                {
                    Node nodeItem = children.item(nodeIndex);

                    if (nodeItem.getNodeName().equals("ImageDescriptor"))
                    {
                        NamedNodeMap map = nodeItem.getAttributes();

                        x = Integer.valueOf(map.getNamedItem("imageLeftPosition").getNodeValue());
                        y = Integer.valueOf(map.getNamedItem("imageTopPosition").getNodeValue());
                    }
                }

                if (disposal.equals("restoreToPrevious"))
                {
                    BufferedImage from = null;

                    for (int i = frameIndex - 1; i >= 0; i--)
                    {
                        if (!metadados.informacoesQuadros[i].disposicao.equals("restoreToPrevious") || frameIndex == 0)
                        {
                            if(i<0){
                                i=0;
                            }
                            from = lerQuadro(i);
                            break;
                        }
                    }

                    {
                        ColorModel model = from.getColorModel();
                        boolean alpha = from.isAlphaPremultiplied();
                        WritableRaster raster = from.copyData(null);
                        metadados.master = new BufferedImage(model, raster, alpha, null);
                    }
                }
                else if (disposal.equals("restoreToBackgroundColor") && metadados.corFundo != null)
                {
                    if (!hasBackround && frameIndex > 1)
                    {
                        metadados.master.createGraphics().fillRect(metadados.lastx, metadados.lasty, metadados.informacoesQuadros[frameIndex - 1].largura, metadados.informacoesQuadros[frameIndex - 1].altura);
                    }
                }

                metadados.master.createGraphics().drawImage(image, x, y, null);
                metadados.lastx = x;
                metadados.lasty = y;
            }
            else
            {
                metadados.master = new BufferedImage(metadados.largura, metadados.altura, BufferedImage.TYPE_INT_ARGB);
                metadados.master.createGraphics().setColor(metadados.corFundo);
                metadados.master.createGraphics().fillRect(0, 0, metadados.master.getWidth(), metadados.master.getHeight());
                hasBackround = image.getWidth() == metadados.largura && image.getHeight() == metadados.altura;

                metadados.master.createGraphics().drawImage(image, 0, 0, null);
            }
            
            BufferedImage copy;
            {
                ColorModel model = metadados.master.getColorModel();
                boolean alpha = metadados.master.isAlphaPremultiplied();
                WritableRaster raster = metadados.master.copyData(null);
                copy = new BufferedImage(model, raster, alpha, null);
                copy = Utils.criarImagemCompativel(copy, this.metadados.largura, this.metadados.altura, this.metadados.manterQualidade);
            }

            metadados.master.flush();
        
            return copy;
        }
        catch (IOException ex)
        {
            return image;
        }
    }

    public void setDimensoes(int largura, int altura, boolean manterQualidade)
    {
        this.metadados.largura = largura;
        this.metadados.altura = altura;
        this.metadados.manterQualidade = manterQualidade;
    }
    
    private final class MetadadosGif
    {
        public int largura = -1;
        public int altura = -1;    
        public Color corFundo = null;
        public int numeroQuadros = -1;
        public int indiceQuadroAtual = -1;
        public long instanteUltimoDesenho = 0;
        public boolean manterQualidade = false;
        
        public int lastx = 0;
        public int lasty = 0;
        
        public final InformacoesQuadro[] informacoesQuadros;
        public BufferedImage master = null;
        public BufferedImage quadroAtual = null;
        public BufferedImage quadroErro = null;                 

        public MetadadosGif(int numeroFrames)
        {
            this.numeroQuadros = numeroFrames;
            this.informacoesQuadros = new InformacoesQuadro[numeroFrames];
            
            this.inicializarListaQuadros();
        }
        
        private void inicializarListaQuadros()
        {
            for (int i = 0; i < numeroQuadros; i++)
            {
                informacoesQuadros[i] = new InformacoesQuadro();
            }
        }
    }
    
    public class InformacoesQuadro
    {
        public int intervalo = 10;
        public String disposicao = "";
        public int largura = -1;
        public int altura = -1;
    }
    
    public ImagemGif clonar() throws IOException, ErroExecucaoBiblioteca
    {
        ImagemGif imagemGif = new ImagemGif();
        
        imagemGif.bytesImagem = this.bytesImagem;
        imagemGif.leitorGif = criarLeitorGif(this.bytesImagem);
        //imagemGif.leitorGif = this.leitorGif;
        imagemGif.metadados = imagemGif.lerMetadadosGif();
        imagemGif.avancarQuadro();
        
        return imagemGif;
    }
}