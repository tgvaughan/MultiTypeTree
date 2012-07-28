# Fit gamma distributions to densities of sampled tree
# heights and compare with exact results for summary statistics

# Clear workspace
rm(list=ls())

require(MASS)

# Data set to use:
mu=0.1

muStr = format(mu, digits=1)

# Fraction of chain to discard:
burninFrac <- 0.2

# Load results of MCMC runs:
dfSame <- read.table(paste('SC_same',muStr,'.log',sep=''), header=T)
dfDiff <- read.table(paste('SC_different',muStr,'.log',sep=''), header=T)

burnin <- floor(length(dfSame$tree.height)*burninFrac)
datSame <- dfSame$tree.height[seq(1,length(dfSame$tree.height))>burnin]
burnin <- floor(length(dfDiff$tree.height)*burninFrac)
datDiff <- dfDiff$tree.height[seq(1,length(dfDiff$tree.height))>burnin]

sameMean <- mean(datSame)
sameVar <- var(datSame)
diffMean <- mean(datDiff)
diffVar <- var(datDiff)

# Set up plot:
pdf(paste('fits',muStr,'.pdf',sep=''), width=7, height=5)

histSame <- hist(datSame, breaks=500, plot=F)
plot(histSame$mids, histSame$density, 'l', lwd=2, col='blue',
     xlab='Tree height',
     ylab='Density',
     main=substitute(paste("Tuning parameter ",mu==muval),list(muval=mu)))

fitSame <- fitdistr(datSame, "gamma")
lines(histSame$mids, dgamma(histSame$mids, shape=fitSame$estimate[[1]],
                            rate=fitSame$estimate[[2]]), lwd=2, lty=2, col='blue')

fitSameMean <- fitSame$estimate[[1]]/fitSame$estimate[[2]]
fitSameVar <- fitSame$estimate[[1]]/fitSame$estimate[[2]]^2

histDiff <- hist(datDiff, breaks=500, plot=F)
lines(histDiff$mids, histDiff$density, lwd=2, col='red')

fitDiff <- fitdistr(datDiff, "gamma")
lines(histDiff$mids, dgamma(histDiff$mids, shape=fitDiff$estimate[[1]],
                            rate=fitDiff$estimate[[2]]), lwd=2, lty=2, col='red')

fitDiffMean <- fitDiff$estimate[[1]]/fitDiff$estimate[[2]]
fitDiffVar <- fitDiff$estimate[[1]]/fitDiff$estimate[[2]]^2

legend('topright', inset=.05,
       c(paste('Same deme: mean=', format(sameMean, digits=4),
               ' var=',format(sameVar, digits=4), sep=''),
         paste('Different demes: mean=', format(diffMean, digits=4),
               ' var=', format(diffVar, digits=4), sep=''),
         'Fitted gamma distributions'),
       lty=c(1,1,2), col=c('blue','red','black'), lwd=2)

dev.off()
