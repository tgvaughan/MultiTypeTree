# Analysis of 3 taxon sampling results

# Clear workspace:
rm(list=ls())

# Load data:
dfSimSame <- read.table('SC_3taxa_same_sim.txt', header=T)
dfSame <- read.table('SC_3taxa_same.log', header=T)
dfSimDiff <- read.table('SC_3taxa_diff_sim.txt', header=T)
dfDiff <- read.table('SC_3taxa_diff.log', header=T)
dfSameComb <- read.table('SC_3taxa_same_combined.log', header=T)
dfDiffComb <- read.table('SC_3taxa_diff_combined.log', header=T)

# Calculate means and variances
burninFrac <- 0.05

noburnin <- function(x, f) {
  return(x[seq(1,length(x))>(f*length(x))])
}

datSimSame <- noburnin(dfSimSame$h, burninFrac)
meanSimSame <- mean(datSimSame)
varSimSame <- var(datSimSame)
semSimSame <- sqrt(varSimSame/length(datSimSame))

datSame <- noburnin(dfSame$tree.height, burninFrac)
meanSame <- mean(datSame)
varSame <- var(datSame)
semSame <- sqrt(varSame/length(datSame))

datSimDiff <- noburnin(dfSimDiff$h, burninFrac)
meanSimDiff <- mean(datSimDiff)
varSimDiff <- var(datSimDiff)
semSimDiff <- sqrt(varSimDiff/length(datSimDiff))

datDiff <- noburnin(dfDiff$tree.height, burninFrac)
meanDiff <- mean(datDiff)
varDiff <- var(datDiff)
semDiff <- sqrt(varDiff/length(datDiff))

# Calculate densities:
hSimSame <- hist(datSimSame, breaks=200, plot=F)
hSame <- hist(datSame, breaks=200, plot=F)
hSimDiff <- hist(datSimDiff, breaks=200, plot=F)
hDiff <- hist(datDiff, breaks=200, plot=F)


# Plot figure
pdf('SC_3taxa.pdf', onefile=F, width=7, height=5)
plot(hSame$mids, hSame$density, 'l', lwd=1, col='blue',
     xlab='Tree height',
     ylab='Rel. frequency',
     main='Coloured WB operator on 3 taxon trees under SC')
lines(hSimSame$mids, hSimSame$density, lwd=2, lty=2, col='blue')
lines(hDiff$mids, hDiff$density, 'l', lwd=1, col='red')
lines(hSimDiff$mids, hSimDiff$density, lwd=2, lty=2, col='red')

# Add descriptive legend
legend('topright', inset=.05,
       c(paste('Same (MCMC): mean=', format(meanSame, digits=4),
               '+/-', format(semSame, digits=2),
               ', var=', format(varSame, digits=4),
               sep=''),
         paste('Same (sim): mean=', format(meanSimSame, digits=4),
               '+/-', format(semSimSame, digits=2),
               ', var=', format(varSimSame, digits=4),
               sep=''),
         paste('Diff (MCMC): mean=', format(meanDiff, digits=4),
               '+/-', format(semDiff, digits=2),
               ', var=', format(varDiff, digits=4),
               sep=''),
         paste('Diff (sim): mean=', format(meanSimDiff, digits=4),
               '+/-', format(semSimDiff, digits=2),
               ', var=', format(varSimDiff, digits=4),
               sep='')),
       lty=c(1,2,1,2),
       lwd=c(1,2,1,2),
       col=c('blue','blue','red','red'))

# Close figure:
dev.off()

# Process combined operator results:
datSameComb <- noburnin(dfSameComb$tree.height, burninFrac)
meanSameComb <- mean(datSameComb)
varSameComb <- var(datSameComb)
semSameComb <- sqrt(varSameComb/length(datSameComb))
datDiffComb <- noburnin(dfDiffComb$tree.height, burninFrac)
meanDiffComb <- mean(datDiffComb)
varDiffComb <- var(datDiffComb)
semDiffComb <- sqrt(varDiffComb/length(datDiffComb))

hSameComb <- hist(datSameComb, breaks=200, plot=F)
hDiffComb <- hist(datDiffComb, breaks=200, plot=F)

# Plot combined operator results:
pdf('SC_3taxa_combined.pdf', onefile=F, width=7, height=5)
plot(hSameComb$mids, hSameComb$density, 'l', lwd=1, col='blue',
     xlab='Tree height',
     ylab='Rel. frequency',
     main='CWBR and tree scale operators on 3 taxon trees under SC')
lines(hSimSame$mids, hSimSame$density, lwd=2, lty=2, col='blue')
lines(hDiffComb$mids, hDiffComb$density, 'l', lwd=1, col='red')
lines(hSimDiff$mids, hSimDiff$density, lwd=2, lty=2, col='red')

# Add descriptive legend
legend('topright', inset=.05,
       c(paste('Same (MCMC): mean=', format(meanSameComb, digits=4),
               '+/-', format(semSameComb, digits=2),
               ', var=', format(varSameComb, digits=4),
               sep=''),
         paste('Same (sim): mean=', format(meanSimSame, digits=4),
               '+/-', format(semSimSame, digits=2),
               ', var=', format(varSimSame, digits=4),
               sep=''),
         paste('Diff (MCMC): mean=', format(meanDiffComb, digits=4),
               '+/-', format(semDiffComb, digits=2),
               ', var=', format(varDiffComb, digits=4),
               sep=''),
         paste('Diff (sim): mean=', format(meanSimDiff, digits=4),
               '+/-', format(semSimDiff, digits=2),
               ', var=', format(varSimDiff, digits=4),
               sep='')),
       lty=c(1,2,1,2),
       lwd=c(1,2,1,2),
       col=c('blue','blue','red','red'))

# Close figure:
dev.off()
